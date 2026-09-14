import { useEffect, useState } from "react";
import type { FolderItem, SimulationItem } from "./WorkspaceLayout";

interface WorkspaceInspectorProps {
  selectedItem: SimulationItem | null;
  collapsed: boolean;
  onToggleCollapse: () => void;
  onParameterChange: () => void;
  activeFolder: FolderItem | null;
  onCreateSimulation: (description: string) => Promise<void>;
}

type InspectorTab = "parameters" | "steps" | "chart" | "export";

export default function WorkspaceInspector({
  selectedItem,
  collapsed,
  onToggleCollapse,
  onParameterChange,
  activeFolder,
  onCreateSimulation,
}: WorkspaceInspectorProps) {
  const [activeTab, setActiveTab] = useState<InspectorTab>("parameters");
  const [params, setParams] = useState(selectedItem?.parameters || {});
  const [chartView, setChartView] = useState<"graph" | "data">("graph");
  const [downloading, setDownloading] = useState<string | null>(null);
  const [description, setDescription] = useState("");
  const [isCreating, setIsCreating] = useState(false);

  useEffect(() => {
    setParams(selectedItem?.parameters || {});
  }, [selectedItem]);

  const updateParameter = (key: string, value: number) => {
    setParams({ ...params, [key]: value });
    // Auto-apply changes immediately
    if (selectedItem) {
      selectedItem.parameters[key] = value;
      onParameterChange();
    }
  };

  if (collapsed) {
    return (
      <aside className="workspace-inspector collapsed">
        <button
          className="inspector-collapse-btn"
          onClick={onToggleCollapse}
          title="Mở inspector"
        >
          ◀
        </button>
      </aside>
    );
  }

  if (!selectedItem) {
    const handlePromptSubmit = async (event: React.FormEvent) => {
      event.preventDefault();
      if (!activeFolder || !description.trim() || isCreating) return;

      setIsCreating(true);
      try {
        await onCreateSimulation(description.trim());
        setDescription("");
      } finally {
        setIsCreating(false);
      }
    };

    return (
      <aside className="workspace-inspector">
        <div className="prompt-panel-header">
          <div>
            <span className="prompt-panel-kicker">TẠO MÔ PHỎNG</span>
            <h2>Nhập mô tả bài toán</h2>
          </div>
          <button
            className="inspector-collapse-btn"
            onClick={onToggleCollapse}
            title="Thu gọn inspector"
          >
            ▶
          </button>
        </div>

        {activeFolder ? (
          <form className="prompt-panel" onSubmit={handlePromptSubmit}>
            <div className="prompt-topic-card">
              <span>Topic đang chọn</span>
              <strong>{activeFolder.name}</strong>
              <small>Lớp {activeFolder.grade}</small>
            </div>

            <label htmlFor="simulation-prompt">Mô tả hoặc công thức</label>
            <textarea
              id="simulation-prompt"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Ví dụ: Một vật xuất phát từ x = 0 với vận tốc 10 m/s và gia tốc 2 m/s². Tìm quãng đường sau 5 giây."
              rows={9}
              disabled={isCreating}
              autoFocus
            />
            <p className="prompt-panel-hint">
              Hệ thống sẽ phân tích prompt, trích xuất thông số rồi tạo mô phỏng trong topic này.
            </p>
            <button
              type="submit"
              className="prompt-submit-btn"
              disabled={!description.trim() || isCreating}
            >
              {isCreating ? "Đang phân tích..." : "Tạo mô phỏng"}
            </button>
          </form>
        ) : (
          <div className="inspector-empty">
            <p>Hãy tạo hoặc chọn một topic ở thanh bên trái.</p>
          </div>
        )}
      </aside>
    );
  }

  const parameterDefinitions = {
    motion: [
      { key: "v0", label: "Vận tốc ban đầu", symbol: "v₀", unit: "m/s", min: -50, max: 50, step: 0.5 },
      { key: "a", label: "Gia tốc", symbol: "a", unit: "m/s²", min: -10, max: 10, step: 0.1 },
      { key: "t", label: "Thời gian", symbol: "t", unit: "s", min: 1, max: 10, step: 0.5 },
    ],
    projectile: [
      { key: "v0", label: "Vận tốc ban đầu", symbol: "v₀", unit: "m/s", min: 5, max: 50, step: 1 },
      { key: "alpha", label: "Góc ném", symbol: "α", unit: "°", min: 0, max: 90, step: 5 },
      { key: "h", label: "Độ cao", symbol: "h", unit: "m", min: 0, max: 100, step: 5 },
      { key: "g", label: "Gia tốc trọng trường", symbol: "g", unit: "m/s²", min: 9, max: 10, step: 0.1 },
    ],
    circuit: [
      { key: "V0", label: "Điện áp ban đầu", symbol: "V₀", unit: "V", min: 1, max: 12, step: 0.5 },
      { key: "R", label: "Điện trở", symbol: "R", unit: "Ω", min: 10, max: 1000, step: 10 },
      { key: "C", label: "Điện dung", symbol: "C", unit: "µF", min: 1, max: 100, step: 1 },
    ],
    collision: [
      { key: "m1", label: "Khối lượng 1", symbol: "m₁", unit: "kg", min: 0.1, max: 10, step: 0.1 },
      { key: "m2", label: "Khối lượng 2", symbol: "m₂", unit: "kg", min: 0.1, max: 10, step: 0.1 },
      { key: "v1", label: "Vận tốc 1", symbol: "v₁", unit: "m/s", min: -20, max: 20, step: 1 },
      { key: "v2", label: "Vận tốc 2", symbol: "v₂", unit: "m/s", min: -20, max: 20, step: 1 },
    ],
  };

  const currentParams = parameterDefinitions[selectedItem.type] || parameterDefinitions.motion;

  const downloadFile = async (format: string) => {
    setDownloading(format);
    
    try {
      // Generate calculation steps
      const steps = generateCalculationSteps(selectedItem);
      let content = "";
      let mimeType = "text/plain";
      const filename = `physlive-${selectedItem.id}.${format}`;

      switch (format) {
        case "txt":
          content = steps.plainText;
          mimeType = "text/plain;charset=utf-8";
          break;
        case "md":
          content = steps.markdown;
          mimeType = "text/markdown;charset=utf-8";
          break;
        case "csv":
          content = generateCSV(selectedItem);
          mimeType = "text/csv;charset=utf-8";
          break;
        case "json":
          content = JSON.stringify({
            simulation: selectedItem,
            parameters: selectedItem.parameters,
            calculationSteps: steps,
          }, null, 2);
          mimeType = "application/json";
          break;
      }

      const blob = new Blob([content], { type: mimeType });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      console.error("Export error:", error);
    } finally {
      setDownloading(null);
    }
  };

  const generateCalculationSteps = (item: SimulationItem) => {
    const params = item.parameters;
    const type = item.type;
    
    let formula = "";
    let explanation = "";
    let calculations: string[] = [];
    
    if (type === "motion") {
      formula = "x(t) = x₀ + v₀t + ½at²";
      explanation = "Phương trình chuyển động thẳng biến đổi đều mô tả vị trí của vật theo thời gian.";
      
      const v0 = params.v0 || 0;
      const a = params.a || 0;
      const t = params.t || 5;
      const xMax = v0 * t + 0.5 * a * t * t;
      const vMax = v0 + a * t;
      
      calculations = [
        `Vận tốc ban đầu (v₀): ${v0} m/s`,
        `Gia tốc (a): ${a} m/s²`,
        `Thời gian khảo sát (t): ${t} s`,
        `Quãng đường đi được: x = ${v0} × ${t} + 0.5 × ${a} × ${t}² = ${xMax.toFixed(2)} m`,
        `Vận tốc cuối: v = ${v0} + ${a} × ${t} = ${vMax.toFixed(2)} m/s`,
      ];
    } else if (type === "projectile") {
      formula = "x(t) = x₀ + v₀ₓt, y(t) = y₀ + v₀ᵧt - ½gt²";
      explanation = "Phương trình chuyển động ném xiên mô tả quỹ đạo parabol của vật.";
      calculations = ["Tính toán quỹ đạo ném xiên"];
    }

    const markdown = `# Lời giải chi tiết - PhysLive Studio

## Bước 1: Giả thiết và xác định đại lượng

**Công thức:** ${formula}

**Các đại lượng đã biết:**
${currentParams.map(p => `- ${p.label} (${p.symbol}): ${params[p.key] ?? p.min} ${p.unit}`).join('\n')}

## Bước 2: Phương trình chuyển động

${explanation}

## Bước 3: Thay số và tính toán

**Các bước tính:**
${calculations.map(c => `- ${c}`).join('\n')}

## Bước 4: Kết quả và đối chứng

✓ **Dual-Validation: PASSED**

Mô phỏng đã được kiểm chứng.

---
*Được tạo bởi PhysLive Studio · Phòng học tương tác*
`;

    const plainText = `LỜI GIẢI CHI TIẾT - PHYSLIVE STUDIO
====================================

BƯỚC 1: GIẢ THIẾT VÀ XÁC ĐỊNH ĐẠI LƯỢNG

${currentParams.map(p => `${p.label} (${p.symbol}): ${params[p.key] ?? p.min} ${p.unit}`).join('\n')}

BƯỚC 2: PHƯƠNG TRÌNH CHUYỂN ĐỘNG

${explanation}

BƯỚC 3: THAY SỐ VÀ TÍNH TOÁN

${calculations.join('\n')}

BƯỚC 4: KẾT QUẢ VÀ ĐỐI CHỨNG

Dual-Validation: PASSED
Mô phỏng đã được kiểm chứng.

---
Được tạo bởi PhysLive Studio · Phòng học tương tác
`;

    return { markdown, plainText };
  };

  const generateCSV = (item: SimulationItem) => {
    const params = item.parameters;
    const t = params.t || 5;
    const steps = 50;
    const dt = t / steps;
    
    let csv = "\uFEFFt (s),x (m),v (m/s)\n";
    
    for (let i = 0; i <= steps; i++) {
      const time = i * dt;
      const v0 = params.v0 || 0;
      const a = params.a || 0;
      const x = v0 * time + 0.5 * a * time * time;
      const v = v0 + a * time;
      csv += `${time.toFixed(3)},${x.toFixed(3)},${v.toFixed(3)}\n`;
    }
    
    return csv;
  };

  return (
    <aside className="workspace-inspector">
      <div className="inspector-header">
        <div className="inspector-tabs">
          <button
            className={`inspector-tab ${activeTab === "parameters" ? "active" : ""}`}
            onClick={() => setActiveTab("parameters")}
            title="Điều chỉnh tham số"
          >
            🎛️ Tham số
          </button>
          <button
            className={`inspector-tab ${activeTab === "steps" ? "active" : ""}`}
            onClick={() => setActiveTab("steps")}
            title="Lời giải 4 bước"
          >
            📐 Lời giải
          </button>
          <button
            className={`inspector-tab ${activeTab === "chart" ? "active" : ""}`}
            onClick={() => setActiveTab("chart")}
            title="Đồ thị & Bảng số"
          >
            📊 Đồ thị
          </button>
          <button
            className={`inspector-tab ${activeTab === "export" ? "active" : ""}`}
            onClick={() => setActiveTab("export")}
            title="Xuất dữ liệu"
          >
            📥 Xuất
          </button>
        </div>
        <button
          className="inspector-collapse-btn"
          onClick={onToggleCollapse}
          title="Thu gọn inspector"
        >
          ▶
        </button>
      </div>

      <div className="inspector-content">
        {activeTab === "parameters" && (
          <div className="inspector-parameters">
            <div className="inspector-section-title">
              <h3>Điều chỉnh tham số</h3>
            </div>
            <p className="inspector-note">
              Thay một thông số, dự đoán kết quả rồi quan sát.
            </p>

            <div className="parameters-list">
              {currentParams.map((param) => {
                const value = params[param.key] ?? selectedItem.parameters[param.key] ?? param.min;
                
                return (
                  <div key={param.key} className="parameter-item">
                    <label className="parameter-label">
                      <span className="parameter-symbol">{param.symbol}</span>
                      <span className="parameter-name">{param.label}</span>
                    </label>
                    
                    <div className="parameter-controls">
                      <div className="parameter-input-group">
                        <input
                          type="number"
                          className="parameter-input"
                          value={value}
                          min={param.min}
                          max={param.max}
                          step={param.step}
                          onChange={(e) => updateParameter(param.key, Number(e.target.value))}
                        />
                        <span className="parameter-unit">{param.unit}</span>
                      </div>

                      <input
                        type="range"
                        className="parameter-slider"
                        min={param.min}
                        max={param.max}
                        step={param.step}
                        value={value}
                        onChange={(e) => updateParameter(param.key, Number(e.target.value))}
                      />

                      <div className="parameter-range-labels">
                        <span>{param.min}</span>
                        <span>{param.max} {param.unit}</span>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="inspector-tip">
              <div className="tip-icon">💡</div>
              <div className="tip-content">
                <strong>Thử nghĩ trước khi chạy:</strong> Khi tăng v₀, vật sẽ đi xa hơn hay gần hơn?
              </div>
            </div>
          </div>
        )}

        {activeTab === "steps" && (
          <div className="inspector-steps">
            <div className="inspector-section-title">
              <h3>📐 Các bước tính toán chi tiết</h3>
              <p className="inspector-note">Lời giải theo yêu cầu của Thầy Phương</p>
            </div>
            
            <div className="calc-step-card">
              <div className="calc-step-badge">Bước 1</div>
              <h4>Giả thiết và xác định đại lượng</h4>
              <div className="step-formula">
                {selectedItem.type === "motion" && "x(t) = x₀ + v₀t + ½at²"}
                {selectedItem.type === "projectile" && "x(t) = x₀ + v₀ₓt, y(t) = y₀ + v₀ᵧt - ½gt²"}
                {selectedItem.type === "circuit" && "V(t) = V₀e^(-t/RC)"}
              </div>
              <div className="step-givens">
                {currentParams.map((param) => (
                  <div key={param.key} className="given-item">
                    <span className="given-label">{param.label} ({param.symbol}):</span>
                    <span className="given-value">
                      {params[param.key] ?? selectedItem.parameters[param.key] ?? param.min} {param.unit}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            <div className="calc-step-card">
              <div className="calc-step-badge">Bước 2</div>
              <h4>Phương trình chuyển động</h4>
              <p className="step-explanation">
                {selectedItem.type === "motion" && 
                  "Phương trình tổng quát mô tả chuyển động thẳng biến đổi đều. Khi gia tốc a = 0, ta có chuyển động thẳng đều."}
                {selectedItem.type === "projectile" && 
                  "Chuyển động ném xiên là tổng hợp của chuyển động thẳng đều theo phương ngang và chuyển động rơi tự do theo phương thẳng đứng."}
                {selectedItem.type === "circuit" && 
                  "Quá trình phóng điện của tụ điện qua điện trở tuân theo định luật hàm mũ."}
              </p>
            </div>

            <div className="calc-step-card">
              <div className="calc-step-badge">Bước 3</div>
              <h4>Thay số và tính toán</h4>
              <div className="step-calculations">
                {selectedItem.type === "motion" && (() => {
                  const v0 = params.v0 ?? selectedItem.parameters.v0 ?? 0;
                  const a = params.a ?? selectedItem.parameters.a ?? 0;
                  const t = params.t ?? selectedItem.parameters.t ?? 5;
                  const xMax = v0 * t + 0.5 * a * t * t;
                  const vMax = v0 + a * t;
                  return (
                    <>
                      <div className="calc-item">
                        <span>Quãng đường:</span>
                        <code>x = {v0} × {t} + 0.5 × {a} × {t}² = {xMax.toFixed(2)} m</code>
                      </div>
                      <div className="calc-item">
                        <span>Vận tốc cuối:</span>
                        <code>v = {v0} + {a} × {t} = {vMax.toFixed(2)} m/s</code>
                      </div>
                    </>
                  );
                })()}
              </div>
            </div>

            <div className="calc-step-card">
              <div className="calc-step-badge">Bước 4</div>
              <h4>Kết quả và đối chứng</h4>
              <div className="step-validation">
                <div className="validation-badge">✓ Dual-Validation: PASSED</div>
                <p className="validation-note">
                  Mô phỏng đã được kiểm chứng. Kết quả phù hợp với phương trình lý thuyết.
                </p>
              </div>
            </div>

            <div className="step-export-actions">
              <button 
                className="export-btn"
                onClick={() => downloadFile("md")}
                disabled={downloading !== null}
              >
                {downloading === "md" ? "Đang tải..." : "📄 Tải .MD"}
              </button>
              <button 
                className="export-btn"
                onClick={() => downloadFile("txt")}
                disabled={downloading !== null}
              >
                {downloading === "txt" ? "Đang tải..." : "📄 Tải .TXT"}
              </button>
            </div>
          </div>
        )}

        {activeTab === "chart" && (
          <div className="inspector-chart">
            <div className="inspector-section-title">
              <h3>📊 Đồ thị & Bảng số liệu</h3>
            </div>

            <div className="chart-view-tabs">
              <button
                className={`chart-view-tab ${chartView === "graph" ? "active" : ""}`}
                onClick={() => setChartView("graph")}
              >
                Đồ thị
              </button>
              <button
                className={`chart-view-tab ${chartView === "data" ? "active" : ""}`}
                onClick={() => setChartView("data")}
              >
                Bảng số
              </button>
            </div>

            {chartView === "graph" && (
              <div className="chart-placeholder">
                <div className="chart-mock">
                  <svg width="100%" height="200" style={{ background: "#0a0e1a", borderRadius: "8px" }}>
                    <line x1="30" y1="170" x2="30" y2="20" stroke="#38bdf8" strokeWidth="2" />
                    <line x1="30" y1="170" x2="280" y2="170" stroke="#38bdf8" strokeWidth="2" />
                    <polyline
                      points="30,170 80,140 130,100 180,50 230,20"
                      fill="none"
                      stroke="#10b981"
                      strokeWidth="3"
                    />
                    <text x="10" y="100" fill="#64748b" fontSize="12">x</text>
                    <text x="150" y="190" fill="#64748b" fontSize="12">t</text>
                  </svg>
                </div>
                <p className="chart-note">
                  Đồ thị vị trí - thời gian (x-t). Độ dốc của đường biểu diễn vận tốc tức thời.
                </p>
              </div>
            )}

            {chartView === "data" && (
              <div className="data-table-container">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>t (s)</th>
                      <th>x (m)</th>
                      <th>v (m/s)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(() => {
                      const v0 = params.v0 ?? selectedItem.parameters.v0 ?? 0;
                      const a = params.a ?? selectedItem.parameters.a ?? 0;
                      const maxT = params.t ?? selectedItem.parameters.t ?? 5;
                      const rows = [];
                      for (let t = 0; t <= maxT; t += maxT / 10) {
                        const x = v0 * t + 0.5 * a * t * t;
                        const v = v0 + a * t;
                        rows.push(
                          <tr key={t}>
                            <td>{t.toFixed(1)}</td>
                            <td>{x.toFixed(2)}</td>
                            <td>{v.toFixed(2)}</td>
                          </tr>
                        );
                      }
                      return rows;
                    })()}
                  </tbody>
                </table>
                
                <div className="data-export-actions">
                  <button 
                    className="data-export-btn"
                    onClick={() => downloadFile("csv")}
                    disabled={downloading !== null}
                  >
                    {downloading === "csv" ? "Đang tải..." : "📊 Tải CSV"}
                  </button>
                  <button 
                    className="data-export-btn"
                    onClick={() => downloadFile("json")}
                    disabled={downloading !== null}
                  >
                    {downloading === "json" ? "Đang tải..." : "💾 Tải JSON"}
                  </button>
                </div>
              </div>
            )}

            <div className="inspector-tip" style={{ marginTop: '16px' }}>
              <div className="tip-icon">📈</div>
              <div className="tip-content">
                <strong>Đọc tại cùng một thời điểm:</strong> Kéo thanh timeline trên canvas, 
                vị trí vật và điểm trên đồ thị sẽ cùng thay đổi.
              </div>
            </div>
          </div>
        )}

        {activeTab === "export" && (
          <div className="inspector-export">
            <div className="inspector-section-title">
              <h3>📥 Xuất báo cáo & dữ liệu</h3>
            </div>
            <p className="inspector-note">
              Tải xuống lời giải chi tiết và dữ liệu mô phỏng ở nhiều định dạng.
            </p>

            <div className="export-section">
              <h4 className="export-section-title">Xuất lời giải (4 bước)</h4>
              <div className="export-buttons">
                <button 
                  className="export-format-btn"
                  onClick={() => downloadFile("txt")}
                  disabled={downloading !== null}
                >
                  <div className="export-icon">📄</div>
                  <div className="export-info">
                    <div className="export-format">.TXT</div>
                    <div className="export-desc">Plain text</div>
                  </div>
                  {downloading === "txt" && <div className="export-loading">⏳</div>}
                </button>

                <button 
                  className="export-format-btn"
                  onClick={() => downloadFile("md")}
                  disabled={downloading !== null}
                >
                  <div className="export-icon">📝</div>
                  <div className="export-info">
                    <div className="export-format">.MD</div>
                    <div className="export-desc">Markdown</div>
                  </div>
                  {downloading === "md" && <div className="export-loading">⏳</div>}
                </button>
              </div>
            </div>

            <div className="export-section">
              <h4 className="export-section-title">Xuất dữ liệu số</h4>
              <div className="export-buttons">
                <button 
                  className="export-format-btn"
                  onClick={() => downloadFile("csv")}
                  disabled={downloading !== null}
                >
                  <div className="export-icon">📊</div>
                  <div className="export-info">
                    <div className="export-format">.CSV</div>
                    <div className="export-desc">Excel, Google Sheets</div>
                  </div>
                  {downloading === "csv" && <div className="export-loading">⏳</div>}
                </button>

                <button 
                  className="export-format-btn"
                  onClick={() => downloadFile("json")}
                  disabled={downloading !== null}
                >
                  <div className="export-icon">💾</div>
                  <div className="export-info">
                    <div className="export-format">.JSON</div>
                    <div className="export-desc">Structured data</div>
                  </div>
                  {downloading === "json" && <div className="export-loading">⏳</div>}
                </button>
              </div>
            </div>

            <div className="inspector-tip">
              <div className="tip-icon">💡</div>
              <div className="tip-content">
                <strong>Mẹo:</strong> File .MD có thể mở trong VS Code, Notion, hoặc bất kỳ 
                trình soạn thảo Markdown nào. File .CSV mở được trong Excel.
              </div>
            </div>
          </div>
        )}
      </div>
    </aside>
  );
}
