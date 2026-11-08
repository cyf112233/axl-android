//! Mobile stand-in for the desktop tray/lightweight-mode implementation.
//!
//! The desktop module drives a tray icon, hides the main window and keeps a
//! small "lightweight" webview alive so the launcher can stay resident while a
//! game runs. None of that exists on Android/iOS, where the OS owns the app
//! lifecycle. The public surface is kept identical so the shared command table
//! keeps working; every operation is a no-op.

use serde::Serialize;
use std::sync::Mutex;
use tauri::{AppHandle, Manager};
use theseus::prelude::CommandPayload;

#[derive(Serialize)]
pub struct PendingCrash {
    pub instance_id: String,
    pub uuid: String,
}

#[derive(Serialize)]
pub struct FrontendReadyPayload {
    pub pending_crashes: Vec<PendingCrash>,
    pub pending_commands: Vec<CommandPayload>,
}

#[derive(Default)]
pub struct LightweightMode(Mutex<Option<String>>);

impl LightweightMode {
    fn set_route(&self, route: String) {
        if let Ok(mut state) = self.0.lock() {
            *state = Some(route);
        }
    }

    pub fn enter_for_close(&self, _app: &AppHandle) -> Result<(), String> {
        Ok(())
    }

    pub fn exit(&self, _app: &AppHandle) -> Result<(), String> {
        Ok(())
    }

    pub fn enter(&self, _app: &AppHandle) -> Result<(), String> {
        Ok(())
    }
}

#[tauri::command]
pub fn lightweight_mode_frontend_ready(
    app: AppHandle,
    route: String,
) -> Result<FrontendReadyPayload, String> {
    app.state::<LightweightMode>().set_route(route);
    Ok(FrontendReadyPayload {
        pending_crashes: Vec::new(),
        pending_commands: Vec::new(),
    })
}

#[tauri::command]
pub fn lightweight_mode_set_route(app: AppHandle, route: String) {
    app.state::<LightweightMode>().set_route(route);
}

#[tauri::command]
pub fn lightweight_mode_enter(_app: AppHandle) -> Result<(), String> {
    Ok(())
}

pub fn init(app: &AppHandle) {
    app.manage(LightweightMode::default());
}
