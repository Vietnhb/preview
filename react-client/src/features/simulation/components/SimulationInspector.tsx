import { useState } from "react";
import Icon from "../../../shared/ui/LearningIcon";
import SaveSimulationPanel from "../../library/components/SaveSimulationPanel";
import { FormulaReview, SimulationParameterControl } from "./SimulationReview";
import type { SimulationWorkspaceModel } from "../hooks/useSimulationWorkspace";
import type { SimulationParameter } from "../api/simulationUnderstandingApi";
import { groupParameters, type PlanModel } from "../model/parameterGroups";

const QUICK_EXAMPLES = [
  {
    title: "Chuyển động thẳng biến đổi đều",
    text: "Một ô tô đang chạy với vận tốc 15 m/s thì hãm phanh chuyển động chậm dần đều với gia tốc 2 m/s². Hãy mô phỏng chuyển động của xe cho đến khi dừng lại.",
  },
  {
    title: "Ném ngang từ độ cao",
    text: "Ném một vật từ độ cao 20 m theo phương ngang với vận tốc đầu 15 m/s. Lấy gia tốc trọng trường g = 9.8 m/s², bỏ qua sức cản không khí.",
  },
  {
    title: "Va chạm đàn hồi 2 xe",
    text: "Xe 1 có khối lượng 2 kg chuyển động với vận tốc 4 m/s đến va chạm đàn hồi trực diện với xe 2 có khối lượng 1 kg đang đứng yên.",
  },
  {
    title: "Con lắc đơn dao động",
    text: "Một con lắc đơn có chiều dài dây 1.5 m, vật nặng 0.5 kg được kéo lệch góc 30 độ so với phương thẳng đứng rồi thả nhẹ không vận tốc đầu.",
  },
];

export default function SimulationInspector({ experiment, explanation, save }: Readonly<Pick<SimulationWorkspaceModel, "experiment" | "explanation" | "save">>) {
  const { simulation, values, validation, updateParameter, resetParameters, applyExample } = experiment;
  const { intent, revision, setRevision, handleRevision } = explanation;
  const { busy, saveOpen, savedMessage, renderError, currentSimulationId, canManageLearningContent,
    folders, onBusyChange, onFolder, onSaved, onClose, onOpen } = save;
  const [navigation, setNavigation] = useState({
    simulation,
    tab: "experiment" as "experiment" | "understand" | "details",
  });
  const inspectorTab = navigation.simulation !== simulation && simulation ? "experiment" : navigation.tab;
  if (navigation.simulation !== simulation) setNavigation({ simulation, tab: inspectorTab });
  const setInspectorTab = (tab: typeof navigation.tab) => setNavigation({ simulation, tab });
  return (
    <aside className="learn-inspector" aria-label="Thông số và giải thích">
      <div className="learn-inspector-nav">
        <div className="learn-tabs" role="tablist">
          <button
            role="tab"
            type="button"
            aria-selected={!saveOpen && inspectorTab === "experiment"}
            disabled={saveOpen && busy}
            onClick={() => { onClose(); setInspectorTab("experiment"); }}
          >
            <Icon name="sliders" /> Thử nghiệm
          </button>
          <button
            role="tab"
            type="button"
            aria-selected={!saveOpen && inspectorTab === "understand"}
            disabled={saveOpen && busy}
            onClick={() => { onClose(); setInspectorTab("understand"); }}
          >
            <Icon name="book" /> Giải thích
          </button>
          <button
            role="tab"
            type="button"
            aria-selected={!saveOpen && inspectorTab === "details"}
            disabled={saveOpen && busy}
            onClick={() => { onClose(); setInspectorTab("details"); }}
          >
            <Icon name="atom" /> Chi tiết
          </button>
        </div>
        {simulation && canManageLearningContent && !currentSimulationId && (
          <button type="button" className="learn-save-button"
            disabled={busy || Boolean(renderError) || saveOpen}
            onClick={onOpen}>
            Lưu mô phỏng
          </button>
        )}
      </div>

      <div className="learn-inspector-body">
        {canManageLearningContent && savedMessage && <div className="simulation-actions">
          <span role="status">{savedMessage}</span>
        </div>}
        {simulation && saveOpen && <SaveSimulationPanel simulation={{ ...simulation, formulas: intent?.formulas, explanation: intent?.explanation }} parameters={{ ...values }} folders={folders}
          onBusyChange={onBusyChange}
          onFolder={onFolder}
          onClose={onClose}
          onSaved={onSaved} />}
        {/* Tab 1: Parameters / Controls */}
        {!saveOpen && inspectorTab === "experiment" && (
          <div>
            <div className="learn-section-title">
              <h3>Thông số mô phỏng</h3>
              {simulation?.parameters?.length ? (
                <button
                  type="button"
                  className="simulation-reset"
                  onClick={resetParameters}
                  title="Khôi phục thông số mặc định"
                >
                  <Icon name="reset" /> Mặc định
                </button>
              ) : null}
            </div>

            {simulation?.parameters?.length ? (
              <ParameterControls
                key={simulation.planSignature ?? simulation.sessionId}
                parameters={simulation.parameters}
                models={simulation.simulationSpec?.physicsModels}
                values={values}
                onChange={updateParameter}
              />
            ) : (
              <div className="simulation-empty">
                {simulation
                  ? "Mô phỏng này không có biến số điều chỉnh."
                  : (
                    <div>
                      <p>Nhập đề bài để xem và điều chỉnh các thông số mô phỏng tại đây.</p>
                      <span className="learn-small-label">GỢI Ý ĐỀ BÀI</span>
                      <div className="simulation-examples">
                        {QUICK_EXAMPLES.map((ex) => (
                          <button key={ex.title} type="button" className="simulation-example" onClick={() => applyExample(ex.text)}>
                            <strong>{ex.title}</strong>
                            <span>{ex.text}</span>
                          </button>
                        ))}
                      </div>
                    </div>
                  )}
              </div>
            )}

            {/* Trạng thái kiểm chứng */}
            <div className="simulation-validation" role="status" aria-live="polite" style={{ marginTop: 20 }}>
              <strong>
                {simulation?.simulationSpec?.runtimeKind === "VISUAL"
                  ? "Trạng thái mô hình: "
                  : "Kiểm tra vật lý: "}
                {validation?.status === "FLAGGED"
                  ? "Cần kiểm tra lại"
                  : validation?.status === "VERIFIED_ANALYTICAL"
                  ? "Đã xác minh bằng số + công thức chuẩn ✓"
                  : validation?.status === "VERIFIED_NUMERICAL"
                  ? "Đã xác minh bằng tính toán số ✓"
                  : validation?.status === "VISUAL_ONLY_UNVERIFIED"
                  ? "Chỉ minh họa — chưa xác minh vật lý"
                  : validation?.status === "UNSUPPORTED"
                  ? "Chưa mô phỏng chính xác được tình huống này"
                  : validation?.status === "PENDING"
                  ? "Đang tính lại và kiểm tra…"
                  : validation?.status === "PAUSED"
                  ? "Đã dừng"
                  : validation?.status === "UNVERIFIED"
                  ? "Chưa kiểm chứng độc lập"
                  : "Đang sẵn sàng"}
              </strong>

              {validation?.verificationScope && <p>Phạm vi xác minh: {validation.verificationScope}</p>}
              {validation?.verificationMethod && (
                <p>Phương pháp xác minh: {validation.verificationMethod}</p>
              )}
              {validation?.absoluteError !== undefined && validation.absoluteError !== null && (
                <p>Sai số lớn nhất: {validation.absoluteError} (tương đối {validation.relativeError ?? "—"})</p>
              )}
              {!!validation?.assumptions?.length && (
                <p>Giả định: {validation.assumptions.join("; ")}</p>
              )}
              {validation?.flags?.map((flag, index) => (
                <p key={index}>{flag}</p>
              ))}
            </div>
          </div>
        )}

        {/* Tab 2: Physics Explanation */}
        {!saveOpen && inspectorTab === "understand" && (
          <div>
            <h3>Giải thích hiện tượng vật lý</h3>
            {intent?.explanation ? (
              <div style={{ marginTop: 10 }}>
                <p className="simulation-explanation">{intent.explanation}</p>
                <FormulaReview intent={intent} />
              </div>
            ) : (
              <p className="simulation-muted" style={{ padding: "12px 0" }}>
                Phần giải thích hiện tượng và lý thuyết vật lý sẽ xuất hiện ở đây sau khi AI phân tích đề bài.
              </p>
            )}

            {!!intent?.defaults?.length && (
              <div className="simulation-defaults" style={{ marginTop: 14 }}>
                <strong>Giả định mặc định</strong>
                <ul>
                  {intent.defaults.map((item, index) => (
                    <li key={index}>{item}</li>
                  ))}
                </ul>
              </div>
            )}

            {intent && (
              <form className="simulation-revision-form" onSubmit={handleRevision}>
                <label htmlFor="inspector-simulation-change">Cần điều chỉnh gì?</label>
                <textarea
                  id="inspector-simulation-change"
                  rows={2}
                  value={revision}
                  onChange={(event) => setRevision(event.target.value)}
                  placeholder="Ví dụ: Thay đổi vận tốc hoặc góc ném..."
                />
                <button disabled={busy || !revision.trim()}>Cập nhật giải thích</button>
              </form>
            )}
          </div>
        )}

        {/* Tab 3: Details & Scene Inventory */}
        {!saveOpen && inspectorTab === "details" && (
          <div>
            <h3>Chi tiết mô hình</h3>

            {intent?.simulationSpec?.requiredObjects?.length ? (
              <div className="simulation-requirement-review" style={{ marginTop: 12 }}>
                <h4>Thành phần AI nhận diện ({intent.simulationSpec.requiredObjects.length})</h4>
                <ul>
                  {intent.simulationSpec.requiredObjects.map((object, index) => (
                    <li key={index}>
                      <strong>
                        {object.count} × {object.label}
                      </strong>
                      {object.shape && object.shape !== "unspecified" && <span> ({object.shape})</span>}
                    </li>
                  ))}
                </ul>
              </div>
            ) : (
              !simulation && (
                <p className="simulation-muted" style={{ padding: "12px 0" }}>
                  AI sẽ chọn các thành phần trực quan theo ngữ cảnh mô tả.
                </p>
              )
            )}
          </div>
        )}
      </div>

      <footer className="learn-inspector-footer">
        <Icon name="atom" />
        <span>PhysLive · Kết quả được kiểm tra bằng hai phương pháp</span>
      </footer>
    </aside>
  );
}

/**
 * Sliders arranged by what the plan's bindings say they are:
 *  - common quantities first;
 *  - a property that several like objects each have (peers) is one control while "linked" — changing it changes
 *    every object — and one control per object once unlinked; peers that start alike are linked by default;
 *  - whatever else belongs to a single participant is listed under that participant.
 */
function ParameterControls({ parameters, models, values, onChange }: Readonly<{
  parameters: SimulationParameter[];
  models?: readonly PlanModel[];
  values: Record<string, number>;
  onChange: (parameter: SimulationParameter, value: number) => void;
}>) {
  const { shared, groups, peers } = groupParameters(parameters, models ?? []);
  const byName = new Map(parameters.map((parameter) => [parameter.name, parameter]));
  const ownerOf = new Map(groups.flatMap((group) => group.parameters.map((parameter) => [parameter.name, group.label] as const)));
  const startsAlike = (name: string) => peers[name].every((peer) => byName.get(peer)?.value === byName.get(peers[name][0])?.value);
  /* family (named by its first member) -> linked?; unset = linked exactly when its members start alike */
  const [links, setLinks] = useState<Record<string, boolean>>({});
  const linked = (family: string) => links[family] ?? startsAlike(family);
  const control = (parameter: SimulationParameter, targets: string[] = [parameter.name], label?: string) => (
    <SimulationParameterControl
      key={parameter.name}
      parameter={label ? { ...parameter, label } : parameter}
      value={values[parameter.name] ?? parameter.value}
      onChange={(next) => { for (const name of targets) { const target = byName.get(name); if (target) onChange(target, next); } }}
    />
  );
  const families = parameters.filter((parameter) => peers[parameter.name]?.[0] === parameter.name);
  if (!families.length && groups.length < 2) return <div className="simulation-controls">{parameters.map((parameter) => control(parameter))}</div>;
  return (
    <div className="simulation-controls">
      {shared.length > 0 && (
        <section className="simulation-control-group">
          {(families.length > 0 || groups.length > 0) && <h4>Chung</h4>}
          {shared.map((parameter) => control(parameter))}
        </section>
      )}
      {families.map((first) => {
        const names = peers[first.name], together = linked(first.name);
        return (
          <section key={first.name} className="simulation-control-group">
            <label className="simulation-control-mode">
              <input type="checkbox" checked={!together}
                onChange={(event) => setLinks((current) => ({ ...current, [first.name]: !event.target.checked }))} />
              Chỉnh riêng từng vật ({names.length})
            </label>
            {together
              ? control(first, names)
              : names.map((name) => { const parameter = byName.get(name); return parameter && control(parameter, [name], (first.label || first.name) + " · " + (ownerOf.get(name) ?? name)); })}
          </section>
        );
      })}
      {groups.map((group) => {
        const own = group.parameters.filter((parameter) => !peers[parameter.name]);
        return own.length > 0 && (
          <section key={group.id} className="simulation-control-group">
            <h4>{group.label}</h4>
            {own.map((parameter) => control(parameter))}
          </section>
        );
      })}
    </div>
  );
}
