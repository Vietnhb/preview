import type { LearningControl } from "../../../utils/learningModel";
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
          const initialValue = Number.isFinite(initialValues[control.key])
            ? initialValues[control.key]
            : control.min;
          const validValue = Number.isFinite(numericValue);
          const min = Math.min(
            control.min,
            initialValue,
            validValue ? numericValue : control.min,
          );
          const max = Math.max(
            control.max,
            initialValue,
            validValue ? numericValue : control.max,
          );
          const invalid =
            draftValue.trim() === "" ||
            !validValue ||
            (control.min >= 0 && numericValue < control.min);
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
                  min={control.min >= 0 ? control.min : undefined}
                  value={draftValue}
                  onChange={(event) =>
                    onChange(control.key, event.target.value)
                  }
                />
                <span>{control.unit}</span>
              </div>
              <input
                className="student-parameter-range"
                aria-label={`Điều chỉnh ${control.label.toLowerCase()}`}
                type="range"
                min={min}
                max={max}
                step={control.step}
                value={validValue ? numericValue : initialValue}
                onChange={(event) => onChange(control.key, event.target.value)}
              />
              <div className="student-parameter-range-labels">
                <span>{min}</span>
                <span>
                  {max} {control.unit}
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

