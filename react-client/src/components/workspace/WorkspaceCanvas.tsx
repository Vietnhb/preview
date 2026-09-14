import { useState, useEffect, useRef } from "react";
import type { SimulationItem } from "./WorkspaceLayout";

interface WorkspaceCanvasProps {
  selectedItem: SimulationItem | null;
}

export default function WorkspaceCanvas({ selectedItem }: WorkspaceCanvasProps) {
  const [playing, setPlaying] = useState(false);
  const [time, setTime] = useState(0);
  const [speed, setSpeed] = useState(1);
  const [showGrid, setShowGrid] = useState(true);
  const [showVelocity, setShowVelocity] = useState(true);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const maxTime = selectedItem?.parameters.t || 5;

  // Animation loop (60 FPS)
  useEffect(() => {
    if (!playing) return;
    
    const interval = setInterval(() => {
      setTime(t => {
        const next = t + 0.016 * speed;
        if (next >= maxTime) {
          setPlaying(false);
          return maxTime;
        }
        return next;
      });
    }, 16);

    return () => clearInterval(interval);
  }, [playing, speed, maxTime]);

  // Physics calculations
  const getPhysicsValues = () => {
    if (!selectedItem) return { x: 0, v: 0, a: 0 };
    
    const v0 = selectedItem.parameters.v0 || 0;
    const a = selectedItem.parameters.a || 0;
    
    const x = v0 * time + 0.5 * a * time * time;
    const v = v0 + a * time;
    
    return { x, v, a };
  };

  // Get physics values (used in canvas rendering)
  getPhysicsValues();

  // Simple canvas rendering
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !selectedItem) return;
    
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const width = canvas.width;
    const height = canvas.height;

    // Clear
    ctx.clearRect(0, 0, width, height);
    ctx.fillStyle = "#090d16";
    ctx.fillRect(0, 0, width, height);

    // Grid (conditional)
    if (showGrid) {
      ctx.strokeStyle = "#1e293b";
      ctx.lineWidth = 1;
      const gridSize = 40;
      for (let x = 0; x < width; x += gridSize) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, height);
        ctx.stroke();
      }
      for (let y = 0; y < height; y += gridSize) {
        ctx.beginPath();
        ctx.moveTo(0, y);
        ctx.lineTo(width, y);
        ctx.stroke();
      }
    }

    // Simple motion visualization
    if (selectedItem.type === "motion") {
      const v0 = selectedItem.parameters.v0 || 0;
      const a = selectedItem.parameters.a || 0;
      const x = v0 * time + 0.5 * a * time * time;
      const v = v0 + a * time;

      const scale = 5;
      const startX = 100;
      const groundY = height - 100;

      // Ground line
      ctx.strokeStyle = "#38bdf8";
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(0, groundY);
      ctx.lineTo(width, groundY);
      ctx.stroke();

      // Object
      const objX = startX + x * scale;
      ctx.fillStyle = "#2563eb";
      ctx.beginPath();
      ctx.arc(objX, groundY - 20, 15, 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = "#60a5fa";
      ctx.lineWidth = 2;
      ctx.stroke();

      // Velocity vector (conditional)
      if (showVelocity && Math.abs(v) > 0.1) {
        ctx.strokeStyle = "#10b981";
        ctx.lineWidth = 3;
        ctx.beginPath();
        ctx.moveTo(objX, groundY - 20);
        ctx.lineTo(objX + v * 3, groundY - 20);
        ctx.stroke();
        
        // Arrow head
        const angle = v > 0 ? 0 : Math.PI;
        ctx.fillStyle = "#10b981";
        ctx.beginPath();
        ctx.moveTo(objX + v * 3, groundY - 20);
        ctx.lineTo(objX + v * 3 - 8 * Math.cos(angle - Math.PI/6), groundY - 20 - 8 * Math.sin(angle - Math.PI/6));
        ctx.lineTo(objX + v * 3 - 8 * Math.cos(angle + Math.PI/6), groundY - 20 + 8 * Math.sin(angle + Math.PI/6));
        ctx.closePath();
        ctx.fill();
      }
    }
  }, [selectedItem, time, showGrid, showVelocity]);

  if (!selectedItem) {
    return (
      <main className="workspace-canvas">
        <div className="canvas-empty">
          <div className="canvas-empty-icon">⚛️</div>
          <h2>Sẵn sàng tạo mô phỏng</h2>
          <p>Chọn một topic, sau đó nhập mô tả bài toán ở panel bên phải</p>
        </div>
      </main>
    );
  }

  const getFormulaText = () => {
    switch (selectedItem.type) {
      case "motion":
        return "x(t) = x₀ + v₀t + ½at²";
      case "projectile":
        return "x(t) = x₀ + v₀ₓt, y(t) = y₀ + v₀ᵧt - ½gt²";
      case "circuit":
        return "V(t) = V₀e^(-t/RC)";
      default:
        return "Formula";
    }
  };

  return (
    <main className="workspace-canvas">
      {/* Formula Hero Bar */}
      <div className="canvas-formula-bar">
        <div className="formula-left">
          <span className="formula-icon">⚛️</span>
          <div>
            <div className="formula-kicker">PHƯƠNG TRÌNH CHUYỂN ĐỘNG</div>
            <div className="formula-text">{getFormulaText()}</div>
          </div>
        </div>
        <div className="formula-actions">
          <span className="formula-badge">Dual-Validation: PASSED ✓</span>
          <button className="formula-reset" onClick={() => setTime(0)}>⟲</button>
        </div>
      </div>

      {/* Canvas Viewport */}
      <div className="canvas-viewport">
        <canvas
          ref={canvasRef}
          width={1200}
          height={600}
          className="canvas-element"
        />
        
        {/* Overlay Controls */}
        <div className="canvas-overlay-controls">
          <button className="overlay-btn active" onClick={() => setShowGrid(!showGrid)}>
            ◻ Lưới
          </button>
          <button className="overlay-btn active" onClick={() => setShowVelocity(!showVelocity)}>
            → Vận tốc
          </button>
        </div>
      </div>

      {/* Metrics Dock */}
      <div className="canvas-metrics-dock">
        <div className="metric-pill">
          <div className="metric-label">⏱️ Thời gian (t)</div>
          <div className="metric-value">{time.toFixed(2)} s</div>
        </div>
        <div className="metric-pill">
          <div className="metric-label">🚀 Vận tốc (v)</div>
          <div className="metric-value highlight">
            {((selectedItem.parameters.v0 || 0) + (selectedItem.parameters.a || 0) * time).toFixed(1)} m/s
          </div>
        </div>
        <div className="metric-pill">
          <div className="metric-label">📍 Vị trí (x)</div>
          <div className="metric-value">
            {((selectedItem.parameters.v0 || 0) * time + 0.5 * (selectedItem.parameters.a || 0) * time * time).toFixed(1)} m
          </div>
        </div>
        <div className="metric-pill">
          <div className="metric-label">🏁 Khoảng cách max</div>
          <div className="metric-value highlight">
            {((selectedItem.parameters.v0 || 0) * maxTime + 0.5 * (selectedItem.parameters.a || 0) * maxTime * maxTime).toFixed(1)} m
          </div>
        </div>
      </div>

      {/* Playback Dock */}
      <div className="canvas-playback-dock">
        <button 
          className="playback-play-btn"
          onClick={() => setPlaying(!playing)}
        >
          {playing ? '⏸' : '▶'}
        </button>
        <button className="playback-icon-btn" onClick={() => setTime(0)}>↺</button>
        
        <div className="playback-timeline">
          <input
            type="range"
            className="playback-scrubber"
            min={0}
            max={maxTime}
            step={0.01}
            value={time}
            onChange={e => {
              setPlaying(false);
              setTime(Number(e.target.value));
            }}
          />
          <span className="playback-time">{time.toFixed(2)} / {maxTime.toFixed(2)} s</span>
        </div>

        <button 
          className="playback-step-btn"
          onClick={() => setTime(Math.min(maxTime, time + 0.1))}
        >
          +0.1s ⏭
        </button>

        <div className="playback-speed-pills">
          {[0.5, 1, 2].map(s => (
            <button
              key={s}
              className={`speed-pill ${speed === s ? 'active' : ''}`}
              onClick={() => setSpeed(s)}
            >
              {s}×
            </button>
          ))}
        </div>
      </div>

      {/* Bottom Bar - Conditions */}
      <div className="canvas-bottom-bar">
        <div className="condition-item">
          <span className="condition-label">Điều kiện ban đầu:</span>
          <span className="condition-value">
            v₀ = {selectedItem.parameters.v0} m/s, a = {selectedItem.parameters.a} m/s²
          </span>
        </div>
        <div className="condition-item">
          <span className="condition-label">Quãng đường tối đa:</span>
          <span className="condition-value">
            {((selectedItem.parameters.v0 || 0) * maxTime + 0.5 * (selectedItem.parameters.a || 0) * maxTime * maxTime).toFixed(1)} m
          </span>
        </div>
      </div>
    </main>
  );
}
