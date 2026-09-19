import { clampControlValue, hasValidControlBounds, isWithinControlBounds, type LearningControl } from "../../../utils/learningModel";
import LearningIcon from "../../common/LearningIcon";

type StudentParameterPanelProps = {
  controls: LearningControl[];
  initialValues: Record<string, number>;
  draft: Record<string, string>;
  adjusting: boolean;
  error: string;
  onChange: (key: string, value: string) => void;
  onReset: () => void;
};

export function StudentParameterPanel({
  controls,
  initialValues,
  draft,
  adjusting,
  error,
  onChange,
  onReset,
}: Readonly<StudentParameterPanelProps>) {
  if (controls.length === 0) return null;
  const dirty = controls.some(
    (control) =>
      draft[control.key] !== undefined &&
      draft[control.key].trim() !== "" &&
      Number(draft[control.key]) !== initialValues[control.key],
  );

  return (
    <section
      className="student-parameter-panel"
      aria-label="Điều chỉnh thông số mô phỏng"
    >
      <div className="student-parameter-heading">
        <div>
          <span className="student-parameter-kicker">THỬ NGHIỆM</span>
          <h3>Điều chỉnh thông số</h3>
        </div>
        <button
          type="button"
          className="student-parameter-reset"
          aria-label="Hoàn tác thông số"
          title="Đặt lại thông số ban đầu"
          disabled={!dirty || adjusting}
          onClick={onReset}
        >
          <LearningIcon name="reset" />
        </button>
      </div>
      <p className="student-parameter-note">
        Kéo thanh trượt hoặc nhập số để xem mô phỏng thay đổi ngay.
      </p>
      <div className="student-parameters">
        {controls.map((control) => {
          const draftValue = draft[control.key] ?? "";
          const numericValue = Number(draftValue);
          const validBounds = hasValidControlBounds(control);
          const initialValue = Number.isFinite(initialValues[control.key])
            ? initialValues[control.key]
            : control.min;
          const validValue = isWithinControlBounds(control, numericValue);
          const rangeValue = clampControlValue(control, validValue ? numericValue : initialValue);
          const invalid = draftValue.trim() === "" || !validValue;
          return (
            <div className="student-parameter" key={control.key}>
              <div className="student-parameter-label">
                <span className="student-variable">{control.symbol}</span>
                <label htmlFor={`student-parameter-${control.key}`}>
                  {control.label}
                </label>
              </div>
              <div className="student-parameter-value">
                <input
                  id={`student-parameter-${control.key}`}
                  aria-invalid={invalid}
                  type="number"
                  inputMode="decimal"
                  step="any"
                  min={control.min}
                  max={control.max}
                  value={draftValue}
                  onChange={(event) =>
                    onChange(control.key, event.target.value)
                  }
                  disabled={!validBounds}
                />
                <span>{control.unit}</span>
              </div>
              <input
                className="student-parameter-range"
                aria-label={`Điều chỉnh ${control.label.toLowerCase()}`}
                type="range"
                min={control.min}
                max={control.max}
                step={control.step}
                value={rangeValue}
                disabled={!validBounds}
                onChange={(event) => onChange(control.key, event.target.value)}
              />
              <div className="student-parameter-range-labels">
                <span>{control.min}</span>
                <span>
                  {control.max} {control.unit}
                </span>
              </div>
            </div>
          );
        })}
      </div>
      {adjusting && (
        <p className="student-parameter-status" aria-live="polite">
          Đang cập nhật mô phỏng…
        </p>
      )}
      {error && (
        <p className="student-parameter-error" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}
