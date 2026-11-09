import { invoke } from '@tauri-apps/api/core'
import { computed, onScopeDispose, ref } from 'vue'

export const networkTargets = [
    { id: 'curseforge_api', name: 'CurseForge API', host: 'api.curseforge.com' },
    { id: 'modrinth_api', name: 'Modrinth API', host: 'api.modrinth.com' },
    { id: 'modrinth_cdn', name: 'Modrinth CDN', host: 'cdn.modrinth.com' },
    { id: 'mojang_api', name: 'Mojang API', host: 'api.mojang.com' },
    { id: 'minecraft_services', name: 'Minecraft Services API', host: 'api.minecraftservices.com' },
] as const

export type NetworkTarget = (typeof networkTargets)[number]['id']
export type NetworkMode = 'launcher' | 'direct'
export type ProbeState = 'idle' | 'queued' | 'running' | 'done' | 'failed' | 'cancelled'

export interface NetworkTestResult {
    target: NetworkTarget
    mode: NetworkMode
    tested_at: string
    launcher_version: string
    platform: string
    url: string
    proxy_mode: 'none' | 'system' | 'custom'
    dns_mode: 'system' | 'doh'
    tls_verification: boolean
    resolved_ips: string[]
    dns_ms: number
    dns_error: string | null
    peer_ip: string | null
    status: number | null
    response_ms: number | null
    total_ms: number
    http_version: string | null
    server: string | null
    served_by: string | null
    cache: string | null
    bytes_received: number
    sample_ms: number | null
    sample_mib_per_second: number | null
    error: string | null
}

export interface NetworkProbe {
    state: ProbeState
    result?: NetworkTestResult
    /** Why the probe ended in `failed`: the backend timed out or rejected the call. */
    error?: 'timeout' | 'request'
}

// The backend bounds every probe (DNS + request) well below this. The ceiling
// exists so a wedged IPC call can never leave a row stuck on "Testing…".
const PROBE_TIMEOUT_MS = 15_000
// Matches the backend's probe slots; more would only compete for bandwidth.
const MAX_CONCURRENT_PROBES = 2

function newRequestId() {
    if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
    // Older Android WebViews lack randomUUID; UUIDv4 is not security sensitive here.
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
        const value = (Math.random() * 16) | 0
        return (char === 'x' ? value : (value & 0x3) | 0x8).toString(16)
    })
}

export function resultStatus(result: NetworkTestResult) {
    if (result.error) return 'failed'
    if (result.status != null && result.status >= 200 && result.status < 300) return 'success'
    if (result.status === 401 || result.status === 403) return 'restricted'
    return 'http_error'
}

export function diagnosticReport(probes: Record<NetworkTarget, NetworkProbe>) {
    return JSON.stringify(
        {
            schema_version: 1,
            generated_at: new Date().toISOString(),
            tests: networkTargets.map((target) => ({
                target: target.id,
                state: probes[target.id].state,
                // Old results remain visible during retests, but must not be exported as new results.
                result: probes[target.id].state === 'done' ? probes[target.id].result : undefined,
            })),
        },
        null,
        2,
    )
}

export function useNetworkDiagnostics() {
    const mode = ref<NetworkMode>('launcher')
    const probes = ref(
        Object.fromEntries(networkTargets.map(({ id }) => [id, { state: 'idle' }])) as Record<
            NetworkTarget,
            NetworkProbe
        >,
    )
    const requests = new Map<NetworkTarget, string>()
    const busy = computed(() =>
        Object.values(probes.value).some(({ state }) => state === 'running' || state === 'queued'),
    )
    const hasResults = computed(() =>
        Object.values(probes.value).some(({ state }) => state === 'done' || state === 'failed'),
    )
    let generation = 0

    async function probe(target: NetworkTarget, run: number, testMode: NetworkMode) {
        if (run !== generation) return
        const requestId = newRequestId()
        requests.set(target, requestId)
        probes.value[target].state = 'running'
        let timer: ReturnType<typeof setTimeout> | undefined
        try {
            const result = await Promise.race([
                invoke<NetworkTestResult | null>('plugin:settings|network_test', {
                    requestId,
                    target,
                    mode: testMode,
                }),
                new Promise<never>((_, reject) => {
                    timer = setTimeout(
                        () => reject(new Error('probe-timeout')),
                        PROBE_TIMEOUT_MS,
                    )
                }),
            ])
            if (run !== generation) return
            probes.value[target] = result
                ? { state: 'done', result }
                : { ...probes.value[target], state: 'cancelled' }
        } catch (error) {
            if (run === generation) {
                const timedOut = error instanceof Error && error.message === 'probe-timeout'
                if (timedOut) {
                    // The backend never answered, so stop it before a retry queues behind it.
                    void invoke('plugin:settings|network_test_cancel', { requestId }).catch(() => {})
                }
                probes.value[target] = {
                    ...probes.value[target],
                    state: 'failed',
                    error: timedOut ? 'timeout' : 'request',
                }
            }
        } finally {
            if (timer !== undefined) clearTimeout(timer)
            if (requests.get(target) === requestId) requests.delete(target)
        }
    }

    async function start(target?: NetworkTarget) {
        if (busy.value) return
        const run = ++generation
        const testMode = mode.value
        const targets = target ? [target] : networkTargets.map(({ id }) => id)
        targets.forEach((id) => {
            probes.value[id].state = 'queued'
        })
        // A single unreachable host used to hold up every probe behind it; two
        // at a time keeps a bad endpoint from hiding the rest of the results.
        const queue = [...targets]
        const worker = async () => {
            while (run === generation) {
                const id = queue.shift()
                if (!id) return
                await probe(id, run, testMode)
            }
        }
        await Promise.all(
            Array.from({ length: Math.min(MAX_CONCURRENT_PROBES, queue.length) }, worker),
        )
    }

    function stop() {
        ++generation
        for (const requestId of requests.values()) {
            void invoke('plugin:settings|network_test_cancel', { requestId }).catch(() => {})
        }
        requests.clear()
        Object.values(probes.value).forEach((probe) => {
            if (probe.state === 'running' || probe.state === 'queued') probe.state = 'cancelled'
        })
    }

    onScopeDispose(stop)
    return { mode, probes, busy, hasResults, start, stop }
}
