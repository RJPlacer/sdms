"use client";

import { BrowserQRCodeReader, type IScannerControls } from "@zxing/browser";
import { useEffect, useRef, useState } from "react";

type ConnectionState = "ready" | "connecting" | "connected" | "expired" | "error";

export default function PhoneScanner({ sessionId }: { sessionId: string }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const controlsRef = useRef<IScannerControls | null>(null);
  const secretRef = useRef("");
  const sendingRef = useRef(false);
  const lastScanRef = useRef({ payload: "", time: 0 });
  const [state, setState] = useState<ConnectionState>("ready");
  const [message, setMessage] = useState("Tap Connect to pair this phone with Athlete Track.");
  const [lastScan, setLastScan] = useState("");
  const [manualValue, setManualValue] = useState("");

  useEffect(() => () => controlsRef.current?.stop(), []);

  async function sendPayload(payload: string) {
    const clean = payload.trim();
    if (!clean || sendingRef.current) return;

    const now = Date.now();
    if (clean === lastScanRef.current.payload && now - lastScanRef.current.time < 2_500) return;
    sendingRef.current = true;
    try {
      const response = await fetch(`/api/scanner/sessions/${sessionId}/events`, {
        method: "POST",
        headers: {
          "Content-Type": "text/plain; charset=utf-8",
          "X-Phone-Secret": secretRef.current,
        },
        body: clean,
      });
      if (response.status === 410) {
        controlsRef.current?.stop();
        setState("expired");
        setMessage("This scanner session has expired. Create a new one on the computer.");
        return;
      }
      if (!response.ok) throw new Error(await response.text());
      lastScanRef.current = { payload: clean, time: now };
      setLastScan(clean);
      setMessage("Sent to the computer. You can scan the next QR code.");
      setManualValue("");
      if (navigator.vibrate) navigator.vibrate(80);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not send this scan.");
    } finally {
      sendingRef.current = false;
    }
  }

  async function connect() {
    if (!videoRef.current || state === "connecting" || state === "connected") return;
    setState("connecting");
    setMessage("Pairing securely…");
    try {
      const pairResponse = await fetch(`/api/scanner/sessions/${sessionId}/pair`, { method: "POST" });
      if (pairResponse.status === 410) {
        setState("expired");
        setMessage("This scanner session has expired. Create a new one on the computer.");
        return;
      }
      if (!pairResponse.ok) throw new Error(await pairResponse.text());
      secretRef.current = await pairResponse.text();

      const reader = new BrowserQRCodeReader(undefined, { delayBetweenScanAttempts: 150 });
      controlsRef.current = await reader.decodeFromConstraints(
        { video: { facingMode: { ideal: "environment" } }, audio: false },
        videoRef.current,
        result => {
          if (result) void sendPayload(result.getText());
        },
      );
      setState("connected");
      setMessage("Connected. Point the rear camera at an Athlete Track QR code.");
    } catch (error) {
      controlsRef.current?.stop();
      setState("error");
      const detail = error instanceof Error ? error.message : "Camera connection failed.";
      setMessage(`${detail} Allow camera access, then create a new session if pairing was completed.`);
    }
  }

  return (
    <main className="scanner-shell">
      <section className="scanner-card">
        <header className="scanner-header">
          <div className="scanner-logo" aria-hidden="true">AT</div>
          <div>
            <p className="eyebrow scanner-eyebrow">Athlete Track</p>
            <h1>Phone QR Scanner</h1>
          </div>
          <span className={`connection-pill ${state}`}>{state === "connected" ? "Connected" : state}</span>
        </header>

        <div className="camera-frame">
          <video ref={videoRef} muted playsInline aria-label="QR scanner camera preview" />
          {state !== "connected" && <div className="camera-placeholder">Camera preview</div>}
          <div className="scan-guide" aria-hidden="true" />
        </div>

        <div className="scanner-content">
          <p className="scanner-status" role="status">{message}</p>
          {state !== "connected" && state !== "expired" && (
            <button className="scanner-button primary" type="button" onClick={connect} disabled={state === "connecting"}>
              {state === "connecting" ? "Connecting…" : "Connect & start camera"}
            </button>
          )}

          {state === "connected" && (
            <form className="manual-entry" onSubmit={event => { event.preventDefault(); void sendPayload(manualValue); }}>
              <label htmlFor="manualQr">Camera not reading the code?</label>
              <div>
                <input id="manualQr" value={manualValue} onChange={event => setManualValue(event.target.value)} placeholder="Paste QR text" />
                <button className="scanner-button" type="submit">Send</button>
              </div>
            </form>
          )}

          {lastScan && (
            <div className="last-scan">
              <strong>Last scan sent</strong>
              <span>{lastScan}</span>
            </div>
          )}
          <p className="scanner-privacy">Only the QR text is relayed. This page cannot access athlete records or photos.</p>
        </div>
      </section>
    </main>
  );
}
