import Icon from "../../../shared/ui/LearningIcon";
import SvgPixiScene from "./SvgPixiScene";
import { RecognitionDisplay, FormulaReview } from "./SimulationReview";
import { visualSource, type SimulationWorkspaceModel } from "../hooks/useSimulationWorkspace";

export default function SimulationStage({ input, preview }: Readonly<Pick<SimulationWorkspaceModel, "input" | "preview">>) {
  const {
    sourceMode, setSourceMode, text, setText, sourceFile, previewUrl, busy, error, acceptImage,
    recognition, correction, setCorrection, editingRecognition, setEditingRecognition, recognitionLowConfidence,
    intent, revision, setRevision, normalize, handleRecognition, handleRevision, generate, reset,
  } = input;
  const { simulation, liveTimeline, runValues, sandboxKey, validation, renderError, setRenderError, restartPreview } = preview;
  return (
    <section className="learn-exploration" aria-label="Quan sát và khám phá">
      <section className="learn-stage" aria-label="Mô phỏng tương tác">
        {simulation ? (
          /* Layout giữa khi có mô phỏng: Canvas card + Header + Restart */
          <div className="simulation-stage-container">
            <div className="simulation-panel-heading">
              <div>
                <span className="simulation-eyebrow">Mô phỏng tương tác</span>
                <h2>Khám phá mô hình</h2>
              </div>
              <button
                type="button"
                className="simulation-restart-button"
                onClick={restartPreview}
              >
                <Icon name="reset" /> Dựng lại
              </button>
            </div>

            {liveTimeline ? (
              <SvgPixiScene
                key={sandboxKey}
                program={simulation.simulationSpec.visualProgram ?? { code: "" }}
                timeline={liveTimeline}
                parameters={runValues}
                models={simulation.simulationSpec.physicsModels}
                fieldMeta={simulation.simulationSpec.solverFieldMeta as Record<string, { unit?: string; label?: string }> | undefined}
                verificationStatus={validation?.status ?? "VISUAL_ONLY_UNVERIFIED"}
                onRenderError={setRenderError}
              />
            ) : (
              <p className="simulation-muted">Chưa có timeline từ backend để hiển thị.</p>
            )}
            {renderError && <button type="button" className="simulation-restart-button" disabled={busy}
              onClick={() => void generate({ code: visualSource(simulation), message: renderError })}>
              {busy ? "AI đang sửa cảnh minh họa…" : "Yêu cầu AI sửa cảnh minh họa"}
            </button>}
            {!renderError && <button type="button" className="simulation-restart-button" disabled={busy} onClick={() => void generate({
              code: visualSource(simulation),
              message: "Redesign the current visual presentation as a polished, contextual illustrated world following the original user description and the rendering contract's art direction. Improve clarity, artwork, environment and composition; preserve the signed physics plan. This is visual design feedback, not physics validation.",
            })}>{busy ? "AI đang thiết kế lại…" : "Thiết kế lại hình ảnh bằng AI"}</button>}
            <p className="simulation-muted">
              Kéo tham số để backend tính lại. Chuyển động, đồ thị và số liệu lấy từ solver backend; cảnh “Minh họa AI” chỉ là phần trình bày, không phải bằng chứng vật lý.
            </p>
            <details>
              <summary>Mã minh họa (PixiJS + SVG) do AI sinh</summary>
              <pre style={{ overflow: "auto", maxHeight: "28rem", whiteSpace: "pre-wrap" }}>{visualSource(simulation)}</pre>
            </details>
            {error && <p className="simulation-error" role="alert">{error}</p>}
          </div>
        ) : (
          /* Layout giữa khi chưa có mô phỏng: Nhập đề, nhận diện, giải thích */
          <div className="simulation-stage-input-container">
            <ol className="simulation-steps" aria-label="Tiến trình dựng mô phỏng">
              <li className={!recognition && !intent ? "active" : "done"}>1. Nhập đề</li>
              <li className={recognition ? "active" : intent ? "done" : ""}>2. Xác nhận</li>
              <li className={intent ? "active" : ""}>3. Dựng mô phỏng</li>
            </ol>

            {error && (
              <div className="simulation-error" role="alert">
                {error}
              </div>
            )}

            {!recognition && !intent && (
              <section className="simulation-card simulation-entry">
                <h2>Nhập đề bài</h2>
                <div className="simulation-source-tabs" role="group" aria-label="Cách nhập đề">
                  {(["TEXT", "LATEX", "IMAGE"] as const).map((mode) => (
                    <button
                      key={mode}
                      type="button"
                      className={sourceMode === mode ? "selected" : ""}
                      aria-pressed={sourceMode === mode}
                      onClick={() => setSourceMode(mode)}
                    >
                      {mode === "TEXT" ? "Văn bản" : mode === "LATEX" ? "LaTeX" : "Ảnh"}
                    </button>
                  ))}
                </div>

                <form onSubmit={normalize}>
                  {sourceMode === "IMAGE" && (
                    <div className="simulation-image-drop">
                      {previewUrl ? (
                        <img src={previewUrl} alt="Ảnh đề bài đã chọn" />
                      ) : (
                        <p>Kéo thả hoặc dán ảnh PNG, JPEG, WebP (tối đa 8 MB), hoặc chọn tệp.</p>
                      )}
                      <label className="simulation-file-label">
                        Chọn ảnh
                        <input
                          type="file"
                          accept="image/png,image/jpeg,image/webp"
                          onChange={(event) => {
                            const file = event.target.files?.[0];
                            if (file) acceptImage(file);
                          }}
                        />
                      </label>
                      {sourceFile && <span>{sourceFile.name}</span>}
                    </div>
                  )}
                  <label htmlFor="simulation-input">
                    {sourceMode === "IMAGE"
                      ? "Ghi chú thêm (không bắt buộc)"
                      : sourceMode === "LATEX"
                      ? "Dán LaTeX"
                      : "Mô tả tình huống vật lý"}
                  </label>
                  <textarea
                    id="simulation-input"
                    value={text}
                    onChange={(event) => setText(event.target.value)}
                    rows={sourceMode === "IMAGE" ? 3 : 5}
                    placeholder={
                      sourceMode === "IMAGE"
                        ? "Thông tin bổ sung nếu cần"
                        : sourceMode === "LATEX"
                        ? "Dán biểu thức LaTeX hoặc đề bài"
                        : "Ví dụ: Một vật được ném ngang từ độ cao 20 m với vận tốc 15 m/s, bỏ qua sức cản không khí."
                    }
                  />
                  <div className="simulation-actions">
                    <button
                      className="simulation-primary-button"
                      disabled={
                        busy ||
                        (sourceMode === "IMAGE" && !sourceFile) ||
                        (sourceMode !== "IMAGE" && !text.trim())
                      }
                    >
                      {busy ? "Đang nhận diện…" : "Nhận diện đề bài"}
                    </button>
                  </div>
                </form>
              </section>
            )}

            {recognition && (
              <section className="simulation-card">
                <span className="simulation-eyebrow">Xác nhận nhận diện</span>
                <h2>Đây có đúng là đề bài của bạn?</h2>
                {recognitionLowConfidence && (
                  <p className="simulation-warning" role="status">
                    Hệ thống chưa chắc chắn về kết quả nhận diện. Hãy sửa lại nội dung trước khi tiếp tục.
                  </p>
                )}
                {recognition.message && <p className="simulation-muted">{recognition.message}</p>}
                <RecognitionDisplay recognition={recognition} />
                {editingRecognition ? (
                  <div className="simulation-correction">
                    <label htmlFor="simulation-correction">Sửa nội dung đã nhận diện</label>
                    <textarea
                      id="simulation-correction"
                      rows={5}
                      value={correction}
                      onChange={(event) => setCorrection(event.target.value)}
                    />
                    <div className="simulation-actions">
                      <button
                        type="button"
                        className="simulation-primary-button"
                        disabled={busy || !correction.trim()}
                        onClick={() => void handleRecognition(false)}
                      >
                        {busy ? "Đang cập nhật…" : "Kiểm tra lại"}
                      </button>
                      {!recognitionLowConfidence && (
                        <button type="button" onClick={() => setEditingRecognition(false)}>
                          Huỷ sửa
                        </button>
                      )}
                    </div>
                  </div>
                ) : (
                  <div className="simulation-actions">
                    <button
                      type="button"
                      className="simulation-primary-button"
                      disabled={busy || recognition.stage !== "RECOGNITION"}
                      onClick={() => void handleRecognition(true)}
                    >
                      {busy ? "Đang phân tích…" : "Đúng, tiếp tục"}
                    </button>
                    <button
                      type="button"
                      onClick={() => setEditingRecognition(true)}
                      disabled={busy}
                    >
                      Sửa lại
                    </button>
                    <button type="button" onClick={reset} disabled={busy}>
                      Làm lại từ đầu
                    </button>
                  </div>
                )}
              </section>
            )}

            {intent && !simulation && (
              <section className="simulation-card">
                <span className="simulation-eyebrow">Hiểu tình huống vật lý</span>
                {intent.stage === "CLARIFY" && (
                  <>
                    <h2>Cần làm rõ một chút</h2>
                    <p className="simulation-explanation">{intent.question || intent.message}</p>
                    <form onSubmit={handleRevision}>
                      <label htmlFor="simulation-answer">Câu trả lời của bạn</label>
                      <textarea
                        id="simulation-answer"
                        rows={3}
                        value={revision}
                        onChange={(event) => setRevision(event.target.value)}
                      />
                      <div className="simulation-actions">
                        <button
                          className="simulation-primary-button"
                          disabled={busy || !revision.trim()}
                        >
                          {busy ? "Đang xử lý…" : "Trả lời"}
                        </button>
                      </div>
                    </form>
                  </>
                )}
                {intent.stage === "UNSUPPORTED" && (
                  <>
                    <h2>Nội dung này chưa phải một tình huống vật lý</h2>
                    <p className="simulation-explanation">
                      {intent.message || intent.explanation}
                    </p>
                    <form onSubmit={handleRevision}>
                      <label htmlFor="simulation-revision">Mô tả lại tình huống</label>
                      <textarea
                        id="simulation-revision"
                        rows={3}
                        value={revision}
                        onChange={(event) => setRevision(event.target.value)}
                      />
                      <div className="simulation-actions">
                        <button
                          className="simulation-primary-button"
                          disabled={busy || !revision.trim()}
                        >
                          Gửi mô tả mới
                        </button>
                      </div>
                    </form>
                  </>
                )}
                {intent.stage === "EXPLAIN" && (
                  <>
                    <h2>Xem lại mô phỏng sẽ dựng</h2>
                    <p className="simulation-explanation">{intent.explanation}</p>
                    <FormulaReview intent={intent} />
                    <p className="simulation-muted">
                      Kết quả dự đoán ở đây chỉ là tạm thời cho tới khi mô phỏng được tính và kiểm tra.
                    </p>
                    <div className="simulation-actions">
                      <button
                        type="button"
                        className="simulation-primary-button"
                        disabled={busy}
                        onClick={() => void generate()}
                      >
                        {busy ? "Đang dựng mô phỏng… (có thể mất 1–3 phút)" : "Dựng mô phỏng"}
                      </button>
                      <button type="button" onClick={reset} disabled={busy}>
                        Làm lại từ đầu
                      </button>
                    </div>
                    <form className="simulation-revision-form" onSubmit={handleRevision}>
                      <label htmlFor="simulation-change">Cần chỉnh gì?</label>
                      <textarea
                        id="simulation-change"
                        rows={2}
                        value={revision}
                        onChange={(event) => setRevision(event.target.value)}
                      />
                      <button disabled={busy || !revision.trim()}>Cập nhật</button>
                    </form>
                  </>
                )}
              </section>
            )}
          </div>
        )}
      </section>
    </section>
  );
}