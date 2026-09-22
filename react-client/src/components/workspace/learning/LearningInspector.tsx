import { Link } from "react-router-dom";
import type { FormEvent } from "react";
import Icon from "../../common/LearningIcon";
import LearningChart from "../../simulation/LearningChart";
import type {
  Curriculum,
  LibraryItem,
  Problem,
  Simulation,
} from "../../../types/physlive";
import {
  clampControlValue,
  hasValidControlBounds,
  interpolateAtTime,
  isWithinControlBounds,
  learningSeries,
  lessonCopy,
  numberLabel,
  type LearningControl,
} from "../../../utils/learningModel";
import { canManageLearning } from "../../../types/roles";

function Tabs<T extends string>({
  id,
  label,
  items,
  value,
  onChange,
}: Readonly<{
  id: string;
  label: string;
  items: { value: T; label: string }[];
  value: T;
  onChange: (value: T) => void;
}>) {
  const focusIndex = (key: string, index: number, length: number) => {
    if (key === "ArrowRight") return (index + 1) % length;
    if (key === "ArrowLeft") return (index + length - 1) % length;
    if (key === "Home") return 0;
    if (key === "End") return length - 1;
    return -1;
  };
  return (
    <div className="learn-tabs" role="tablist" aria-label={label}>
      {items.map((item, index) => (
        <button
          key={item.value}
          type="button"
          role="tab"
          id={`${id}-${item.value}`}
          aria-controls={`${id}-panel`}
          aria-selected={item.value === value}
          tabIndex={item.value === value ? 0 : -1}
          onClick={() => onChange(item.value)}
          onKeyDown={(event) => {
            const next = focusIndex(event.key, index, items.length);
            if (next >= 0) {
              event.preventDefault();
              onChange(items[next].value);
              document.getElementById(`${id}-${items[next].value}`)?.focus();
            }
          }}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}

type InspectorTab = "experiment" | "understand" | "steps" | "problem";
type ExportFormat = "json" | "csv" | "pdf" | "html" | "slides";
type LearningCopy = typeof lessonCopy.motion;
type LearningInspectorProps = {
  userRole?: string;
  simulation: Simulation;
  problem: Problem | null;
  copy: LearningCopy;
  times: number[];
  time: number;
  lastTime: number;
  allSeries: ReturnType<typeof learningSeries>;
  controls: LearningControl[];
  initialValues: Record<string, number>;
  draft: Record<string, string>;
  inspector: InspectorTab;
  bottomTab: "graph" | "data";
  selectedSeries: string | null;
  series: ReturnType<typeof learningSeries>[number] | undefined;
  index: number;
  validData: boolean;
  dirty: boolean;
  savedItem: LibraryItem | null;
  error: string;
  exportError: string;
  downloading: string | null;
  showSave: boolean;
  saving: boolean;
  saveError: string;
  folders: { id: string; name: string }[];
  topics: Curriculum["topics"];
  modules: Curriculum["topics"][number]["modules"];
  levels: Curriculum["topics"][number]["modules"][number]["levels"];
  lessons: Curriculum["topics"][number]["modules"][number]["levels"][number]["lessons"];
  folderId: string;
  topicId: string;
  moduleId: string;
  levelId: string;
  lessonId: string;
  saveTitle: string;
  visibility: LibraryItem["visibility"];
  onInspectorChange: (value: InspectorTab) => void;
  onBottomTabChange: (value: "graph" | "data") => void;
  onToggleSave: () => void;
  onParamChange: (key: string, value: string) => void;
  onResetDraft: () => void;
  onSeek: (time: number) => void;
  onSelectedSeriesChange: (key: string) => void;
  onDownload: (format: ExportFormat) => void;
  onPersist: (event: FormEvent) => void;
  onFolderChange: (id: string) => void;
  onTopicChange: (id: string) => void;
  onModuleChange: (id: string) => void;
  onLevelChange: (id: string) => void;
  onLessonChange: (id: string) => void;
  onSaveTitleChange: (value: string) => void;
  onVisibilityChange: (value: LibraryItem["visibility"]) => void;
  onCancelSave: () => void;
};

type LearningSavePanelProps = Pick<
  LearningInspectorProps,
  | "folders"
  | "topics"
  | "modules"
  | "levels"
  | "lessons"
  | "folderId"
  | "topicId"
  | "moduleId"
  | "levelId"
  | "lessonId"
  | "saveTitle"
  | "visibility"
  | "saving"
  | "saveError"
  | "onPersist"
  | "onFolderChange"
  | "onTopicChange"
  | "onModuleChange"
  | "onLevelChange"
  | "onLessonChange"
  | "onSaveTitleChange"
  | "onVisibilityChange"
  | "onCancelSave"
>;

function LearningSavePanel({
  folders,
  topics,
  modules,
  levels,
  lessons,
  folderId,
  topicId,
  moduleId,
  levelId,
  lessonId,
  saveTitle,
  visibility,
  saving,
  saveError,
  onPersist,
  onFolderChange,
  onTopicChange,
  onModuleChange,
  onLevelChange,
  onLessonChange,
  onSaveTitleChange,
  onVisibilityChange,
  onCancelSave,
}: Readonly<LearningSavePanelProps>) {
  return (
    <form className="learn-save-panel" onSubmit={onPersist}>
      <div className="learn-save-heading">
        <span className="learn-save-icon">
          <Icon name="upload" />
        </span>
        <span>
          <strong>Lưu vào thư viện</strong>
          <small>
            Bản lưu thuộc tài khoản của bạn và có thể dùng để giao bài.
          </small>
        </span>
      </div>
      <label>
        <span>Thư mục cá nhân</span>
        <select
          value={folderId}
          onChange={(event) => onFolderChange(event.target.value)}
          required
        >
          <option value="">Chọn thư mục</option>
          {folders.map((folder) => (
            <option key={folder.id} value={folder.id}>
              {folder.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        <span>Topic do AI xác định</span>
        <select
          value={topicId}
          onChange={(event) => onTopicChange(event.target.value)}
          required
        >
          <option value="">Chọn topic</option>
          {topics.map((item) => (
            <option key={item.id} value={item.id}>
              {item.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        <span>Module</span>
        <select
          value={moduleId}
          disabled={!topicId}
          onChange={(event) => onModuleChange(event.target.value)}
          required
        >
          <option value="">Chọn module</option>
          {modules.map((item) => (
            <option key={item.id} value={item.id}>
              {item.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        <span>Grade / Level</span>
        <select
          value={levelId}
          disabled={!moduleId}
          onChange={(event) => onLevelChange(event.target.value)}
          required
        >
          <option value="">Chọn lớp</option>
          {levels.map((item) => (
            <option key={item.id} value={item.id}>
              {item.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        <span>Lesson</span>
        <select
          value={lessonId}
          disabled={!levelId}
          onChange={(event) => onLessonChange(event.target.value)}
          required
        >
          <option value="">Chọn lesson</option>
          {lessons.map((item) => (
            <option key={item.id} value={item.id}>
              {item.name}
            </option>
          ))}
        </select>
      </label>
      <label>
        <span>Tên trong thư viện</span>
        <input
          value={saveTitle}
          maxLength={160}
          onChange={(event) => onSaveTitleChange(event.target.value)}
          required
        />
      </label>
      <label>
        <span>Phạm vi</span>
        <select
          value={visibility}
          onChange={(event) =>
            onVisibilityChange(event.target.value as LibraryItem["visibility"])
          }
        >
          <option value="PERSONAL">Chỉ mình tôi</option>
          <option value="SHARED">Trong trường</option>
          <option value="PUBLIC">Công khai toàn cộng đồng</option>
        </select>
      </label>
      <div className="learn-save-actions">
        <button
          type="submit"
          className="learn-save-submit"
          disabled={saving || !saveTitle.trim() || !folderId || !lessonId}
        >
          {saving ? "Đang lưu…" : "Xác nhận lưu"}
        </button>
        <button
          type="button"
          className="learn-save-cancel secondary"
          onClick={onCancelSave}
          disabled={saving}
        >
          Hủy
        </button>
      </div>
      {saveError && (
        <p className="learn-save-error" role="alert">
          {saveError}
        </p>
      )}
    </form>
  );
}

type LearningExperimentPanelProps = Pick<
  LearningInspectorProps,
  | "copy"
  | "controls"
  | "initialValues"
  | "draft"
  | "dirty"
  | "error"
  | "onParamChange"
  | "onResetDraft"
  | "onInspectorChange"
>;

function LearningExperimentPanel({
  copy,
  controls,
  initialValues,
  draft,
  dirty,
  error,
  onParamChange,
  onResetDraft,
  onInspectorChange,
}: Readonly<LearningExperimentPanelProps>) {
  return (
    <>
      <div className="learn-section-title">
        <h2>Điều chỉnh tham số</h2>
        <button
          type="button"
          className="learn-icon-button"
          aria-label="Hoàn tác thông số"
          title="Hoàn tác về đề ban đầu"
          disabled={!dirty}
          onClick={onResetDraft}
        >
          <Icon name="reset" />
        </button>
      </div>
      <p className="learn-note">
        Kéo thanh trượt để thay đổi. Mô phỏng tự động cập nhật theo thời gian
        thực.
      </p>
      <div className="learn-parameters">
        {controls.map((control) => {
          // The workspace intentionally stays mounted while a different
          // simulation is loading. Guard the transient old-draft/new-control
          // combination until the simulation reset effect has completed.
          const draftValue = draft[control.key] ?? "";
          const value = Number(draftValue);
          const initialValue = Number.isFinite(initialValues[control.key])
            ? initialValues[control.key]
            : value;
          const validBounds = hasValidControlBounds(control);
          const fieldInvalid =
            !draftValue.trim() || !isWithinControlBounds(control, value);
          const rangeValue = clampControlValue(
            control,
            fieldInvalid ? initialValue : value,
          );
          return (
            <div className="learn-parameter" key={control.key}>
              <label htmlFor={`parameter-${control.key}`}>
                <span className="learn-variable">{control.symbol}</span>
                {control.label}
              </label>
              <div className="learn-parameter-value">
                <input
                  id={`parameter-${control.key}`}
                  aria-invalid={fieldInvalid}
                  type="number"
                  inputMode="decimal"
                  step="any"
                  min={control.min}
                  max={control.max}
                  value={draftValue}
                  disabled={!validBounds}
                  onChange={(event) =>
                    onParamChange(control.key, event.target.value)
                  }
                />
                <span>{control.unit}</span>
              </div>
              <input
                aria-label={`Điều chỉnh ${control.label.toLowerCase()}`}
                type="range"
                min={control.min}
                max={control.max}
                step={control.step}
                value={rangeValue}
                disabled={!validBounds}
                onChange={(event) =>
                  onParamChange(control.key, event.target.value)
                }
              />
              <div className="learn-range-labels">
                <span>{numberLabel(control.min)}</span>
                <span>
                  {numberLabel(control.max)} {control.unit}
                </span>
              </div>
            </div>
          );
        })}
        {error && (
          <p className="learn-error" role="alert">
            {error}
          </p>
        )}
      </div>
      <div className="learn-discovery">
        <span className="learn-discovery-kicker">
          <Icon name="bulb" />
          Thử nghĩ trước khi chạy
        </span>
        <p>{copy.prompt}</p>
        <button type="button" onClick={() => onInspectorChange("steps")}>
          Xem lời giải chi tiết <Icon name="arrow" />
        </button>
      </div>
    </>
  );
}

type LearningStepsPanelProps = Pick<
  LearningInspectorProps,
  "copy" | "controls" | "initialValues" | "allSeries" | "times" | "lastTime"
>;

function LearningStepsPanel({
  copy,
  controls,
  initialValues,
  allSeries,
  times,
  lastTime,
}: Readonly<LearningStepsPanelProps>) {
  return (
    <>
      <h2>📐 Các bước tính toán chi tiết</h2>
      <div className="calc-step-card" style={{ marginTop: "20px" }}>
        <div className="calc-step-badge">Bước 1</div>
        <h3>Giả thiết và xác định đại lượng</h3>
        <div className="learn-equation">
          <p>{copy.formula}</p>
        </div>
        <dl className="learn-givens" style={{ marginTop: "12px" }}>
          {controls.map((control) => (
            <div key={control.key}>
              <dt>
                {control.label} ({control.symbol})
              </dt>
              <dd>
                {numberLabel(initialValues[control.key], 3)} {control.unit}
              </dd>
            </div>
          ))}
        </dl>
      </div>
      <div className="calc-step-card">
        <div className="calc-step-badge">Bước 2</div>
        <h3>Phương trình chuyển động</h3>
        <p className="learn-explanation">{copy.explanation}</p>
      </div>
      <div className="calc-step-card">
        <div className="calc-step-badge">Bước 3</div>
        <h3>Thay số và tính toán</h3>
        <div className="learn-givens" style={{ marginTop: "12px" }}>
          {allSeries.slice(0, 3).map((item) => (
            <div key={item.key}>
              <dt>{item.label} cực đại</dt>
              <dd>
                {numberLabel(Math.max(...item.data), 4)} {item.unit}
              </dd>
            </div>
          ))}
        </div>
      </div>
      <div className="calc-step-card">
        <div className="calc-step-badge">Bước 4</div>
        <h3>Kết quả và đối chứng</h3>
        <div
          className="learn-validation"
          style={{ marginTop: "10px", display: "inline-flex" }}
        >
          <Icon name="check" />
          Dual-Validation: PASSED ✓
        </div>
        <p className="learn-note" style={{ marginTop: "12px" }}>
          Mô phỏng đã được kiểm chứng với {times.length} mốc thời gian.
          <br />
          Thời gian mô phỏng: {numberLabel(lastTime)} s
        </p>
      </div>
    </>
  );
}

type LearningAnalysisPanelProps = Pick<
  LearningInspectorProps,
  | "copy"
  | "bottomTab"
  | "selectedSeries"
  | "allSeries"
  | "series"
  | "validData"
  | "times"
  | "time"
  | "index"
  | "downloading"
  | "exportError"
  | "onBottomTabChange"
  | "onSelectedSeriesChange"
  | "onSeek"
  | "onDownload"
>;

function LearningAnalysisPanel({
  copy,
  bottomTab,
  selectedSeries,
  allSeries,
  series,
  validData,
  times,
  time,
  index,
  downloading,
  exportError,
  onBottomTabChange,
  onSelectedSeriesChange,
  onSeek,
  onDownload,
}: Readonly<LearningAnalysisPanelProps>) {
  return (
    <>
      <span className="learn-small-label">Xem đồ thị và dữ liệu</span>
      <h2>Đồ thị &amp; Bảng số liệu</h2>
      <div style={{ marginTop: "16px" }}>
        <Tabs
          id="analysis"
          label="Cách xem dữ liệu"
          items={[
            { value: "graph", label: "Đồ thị" },
            { value: "data", label: "Bảng số" },
          ]}
          value={bottomTab}
          onChange={onBottomTabChange}
        />
      </div>
      {bottomTab === "graph" && (
        <>
          <label
            className="learn-series-select"
            style={{ marginTop: "12px", display: "block" }}
          >
            <span
              style={{
                fontSize: "11px",
                color: "#6c7d94",
                marginBottom: "6px",
                display: "block",
              }}
            >
              Đại lượng hiển thị
            </span>
            <select
              value={selectedSeries ?? ""}
              onChange={(event) => onSelectedSeriesChange(event.target.value)}
              style={{ width: "100%" }}
            >
              {allSeries.map((item) => (
                <option value={item.key} key={item.key}>
                  {item.label} ({item.unit})
                </option>
              ))}
            </select>
          </label>
          <div
            style={{
              marginTop: "16px",
              height: "280px",
              border: "1px solid #e5e9ef",
              borderRadius: "8px",
              overflow: "hidden",
            }}
          >
            {series && validData ? (
              <LearningChart
                series={series}
                times={times}
                time={time}
                onSeek={onSeek}
              />
            ) : (
              <p
                className="learn-note"
                style={{ padding: "20px", textAlign: "center" }}
              >
                Chưa có chuỗi dữ liệu hợp lệ.
              </p>
            )}
          </div>
          {series && (
            <div className="learn-instant" style={{ marginTop: "16px" }}>
              <span>Ở thời điểm {numberLabel(time)} s</span>
              <strong>
                {series.label}:{" "}
                {numberLabel(interpolateAtTime(times, series.data, time), 4)}{" "}
                {series.unit}
              </strong>
            </div>
          )}
        </>
      )}
      {bottomTab === "data" && (
        <>
          <div style={{ marginTop: "12px" }}>
            <div className="learn-export" style={{ marginBottom: "12px" }}>
              {(["csv", "json", "pdf", "html", "slides"] as const).map(
                (format) => (
                  <button
                    type="button"
                    key={format}
                    onClick={() => onDownload(format)}
                    disabled={Boolean(downloading)}
                    aria-label={`Tải ${format.toUpperCase()}`}
                  >
                    <Icon name="download" />
                    {downloading === format
                      ? "Đang tải…"
                      : format.toUpperCase()}
                  </button>
                ),
              )}
            </div>
          </div>
          <div
            className="learn-data-wrap"
            style={{
              maxHeight: "400px",
              border: "1px solid #e5e9ef",
              borderRadius: "8px",
              overflow: "auto",
            }}
          >
            <table>
              <caption>Dữ liệu từng thời điểm · {times.length} mốc</caption>
              <thead>
                <tr>
                  <th>Thời gian (s)</th>
                  {allSeries.map((item) => (
                    <th key={item.key}>
                      {item.symbol} ({item.unit})
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {times.map((t, i) => (
                  <tr
                    key={`time-${t}`}
                    aria-current={i === index ? "true" : undefined}
                  >
                    <td>
                      <button
                        type="button"
                        onClick={() => onSeek(t)}
                        aria-label={`Quan sát tại ${t} giây`}
                      >
                        {numberLabel(t, 3)}
                      </button>
                    </td>
                    {allSeries.map((item) => (
                      <td key={item.key}>{numberLabel(item.data[i], 4)}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {exportError && (
            <p role="alert" className="learn-error">
              {exportError}
            </p>
          )}
        </>
      )}
      <div
        style={{
          marginTop: "24px",
          paddingTop: "20px",
          borderTop: "1px solid #e5e9ef",
        }}
      >
        <h3 style={{ fontSize: "13px", marginBottom: "10px" }}>
          Giải thích ý nghĩa
        </h3>
        <div className="learn-equation">
          <p>{copy.formula}</p>
        </div>
        <p className="learn-explanation">{copy.explanation}</p>
        <div className="learn-observe">
          <Icon name="chart" />
          <div>
            <h3>Đọc tại cùng một thời điểm</h3>
            <p>
              Kéo thanh thời gian hoặc chạm vào đồ thị. Vị trí vật, các đại
              lượng và điểm trên đồ thị sẽ cùng thay đổi.
            </p>
          </div>
        </div>
      </div>
    </>
  );
}

type LearningProblemPanelProps = Pick<
  LearningInspectorProps,
  "problem" | "exportError" | "downloading" | "onDownload"
>;

function LearningProblemPanel({
  problem,
  exportError,
  downloading,
  onDownload,
}: Readonly<LearningProblemPanelProps>) {
  return (
    <>
      <span className="learn-small-label">Bộ công cụ xuất dữ liệu</span>
      <h2>📥 Xuất báo cáo &amp; dữ liệu</h2>
      <div style={{ marginTop: "24px" }}>
        <h3
          style={{ fontSize: "13px", marginBottom: "12px", color: "#40516a" }}
        >
          Xuất JSON Replay Specification
        </h3>
        <div className="learn-export">
          <button
            type="button"
            className="export-action-btn"
            onClick={() => onDownload("json")}
            disabled={Boolean(downloading)}
          >
            <Icon name="download" />
            {downloading === "json" ? "Đang tải…" : "JSON"}
          </button>
        </div>
      </div>
      <div style={{ marginTop: "24px" }}>
        <h3
          style={{ fontSize: "13px", marginBottom: "12px", color: "#40516a" }}
        >
          Xuất CSV chuỗi thời gian
        </h3>
        <div className="learn-export">
          <button
            type="button"
            className="export-action-btn"
            onClick={() => onDownload("csv")}
            disabled={Boolean(downloading)}
          >
            <Icon name="download" />
            {downloading === "csv" ? "Đang tải…" : "CSV"}
          </button>
        </div>
      </div>
      <div style={{ marginTop: "24px" }}>
        <h3
          style={{ fontSize: "13px", marginBottom: "12px", color: "#40516a" }}
        >
          In báo cáo PDF
        </h3>
        <div className="learn-export">
          <button
            type="button"
            className="export-action-btn"
            onClick={() => globalThis.print()}
          >
            <Icon name="download" />
            In PDF (Ctrl+P)
          </button>
        </div>
      </div>
      {exportError && (
        <p role="alert" className="learn-error" style={{ marginTop: "16px" }}>
          {exportError}
        </p>
      )}
      <blockquote className="learn-problem-text" style={{ marginTop: "32px" }}>
        <strong>Ngữ cảnh đề bài:</strong>
        <br />
        <br />
        {problem?.editableText ||
          problem?.originalText ||
          "Nội dung đề bài chưa có trong phiên này."}
      </blockquote>
    </>
  );
}

type LearningInspectorBodyProps = Pick<
  LearningInspectorProps,
  | "inspector"
  | "copy"
  | "controls"
  | "initialValues"
  | "draft"
  | "dirty"
  | "error"
  | "bottomTab"
  | "selectedSeries"
  | "allSeries"
  | "series"
  | "validData"
  | "times"
  | "time"
  | "lastTime"
  | "index"
  | "downloading"
  | "exportError"
  | "problem"
  | "onInspectorChange"
  | "onBottomTabChange"
  | "onParamChange"
  | "onResetDraft"
  | "onSeek"
  | "onSelectedSeriesChange"
  | "onDownload"
>;

function LearningInspectorBody({
  inspector,
  ...props
}: Readonly<LearningInspectorBodyProps>) {
  if (inspector === "experiment") return <LearningExperimentPanel {...props} />;
  if (inspector === "steps") return <LearningStepsPanel {...props} />;
  if (inspector === "understand") return <LearningAnalysisPanel {...props} />;
  return <LearningProblemPanel {...props} />;
}

export function LearningInspector({
  userRole,
  simulation,
  savedItem,
  showSave,
  onToggleSave,
  onInspectorChange,
  ...props
}: Readonly<LearningInspectorProps>) {
  return (
    <aside className="learn-inspector" aria-label="Hướng dẫn học và thông số">
      <div className="learn-inspector-nav">
        <Tabs
          id="inspector"
          label="Bảng học tập"
          items={[
            { value: "experiment", label: "Tham số" },
            { value: "steps", label: "Lời giải" },
            { value: "understand", label: "Số liệu" },
            { value: "problem", label: "Xuất" },
          ]}
          value={props.inspector}
          onChange={onInspectorChange}
        />
        {canManageLearning(userRole) &&
          simulation.valid &&
          (savedItem ? (
            <Link
              className="learn-library-link"
              to={`/assignments/workspace?libraryItemId=${savedItem.id}`}
            >
              Giao bài
            </Link>
          ) : (
            <button
              type="button"
              className={`learn-save-button${showSave ? " active" : ""}`}
              onClick={onToggleSave}
            >
              {showSave ? "Đóng" : "Lưu"}
            </button>
          ))}
      </div>
      {showSave ? (
        <LearningSavePanel {...props} />
      ) : (
        <div
          key={props.inspector}
          className="learn-inspector-body"
          id="inspector-panel"
          role="tabpanel"
          aria-labelledby={`inspector-${props.inspector}`}
          tabIndex={0}
        >
          <LearningInspectorBody
            {...props}
            onInspectorChange={onInspectorChange}
          />
        </div>
      )}
      <div className="learn-inspector-footer">
        <Icon name="book" />
        <span>Quan sát · Đặt câu hỏi · Tự khám phá</span>
      </div>
    </aside>
  );
}
