import { Heading, IconButton, Text, TextField } from "@radix-ui/themes";
import { clampControlValue, hasValidControlBounds, isWithinControlBounds, type LearningControl } from "../../simulation/model/learningModel";
import LearningIcon from "../../../shared/ui/LearningIcon";
import { StudentSlider } from "./StudentSlider";

type StudentParameterPanelProps = {
  controls: LearningControl[];
  initialValues: Record<string, number>;
  draft: Record<string, string>;
  error: string;
  onChange: (key: string, value: string) => void;
  onReset: () => void;
};

export function StudentParameterPanel({ controls, initialValues, draft, error, onChange, onReset }: Readonly<StudentParameterPanelProps>) {
  if (controls.length === 0) return null;
  const dirty = controls.some(control => draft[control.key] !== undefined && (draft[control.key].trim() === "" || Number(draft[control.key]) !== initialValues[control.key]));
  return <section className="student-lab-controls" aria-label="Điều chỉnh thông số mô phỏng">
    <div className="student-lab-heading"><Heading as="h2" size="4">Thông số mô phỏng</Heading><IconButton type="button" variant="soft" size="1" aria-label="Hoàn tác thông số" title="Đặt lại thông số ban đầu" disabled={!dirty} onClick={onReset}><LearningIcon name="reset" /></IconButton></div>
    <div className="student-lab-fields">{controls.map(control => {
      const draftValue = draft[control.key] ?? "";
      const numericValue = Number(draftValue);
      const validBounds = hasValidControlBounds(control);
      const initialValue = Number.isFinite(initialValues[control.key]) ? initialValues[control.key] : control.min;
      const validValue = isWithinControlBounds(control, numericValue);
      const rangeValue = clampControlValue(control, validValue ? numericValue : initialValue);
      const invalid = draftValue.trim() === "" || !validValue;
      return <div className="student-lab-control" key={control.key}>
        <div className="student-lab-label"><span className="student-lab-symbol">{control.symbol}</span><Text as="label" size="2" htmlFor={`student-parameter-${control.key}`}>{control.label}</Text></div>
        <TextField.Root className="student-lab-value" id={`student-parameter-${control.key}`} size="1" aria-invalid={invalid} type="number" inputMode="decimal" step="any" min={control.min} max={control.max} value={draftValue} onChange={event => onChange(control.key, event.target.value)} disabled={!validBounds}><TextField.Slot side="right"><Text size="1" color="gray">{control.unit}</Text></TextField.Slot></TextField.Root>
        <StudentSlider className="student-lab-range" size="1" label={`Điều chỉnh ${control.label.toLowerCase()}`} min={validBounds ? control.min : 0} max={validBounds && control.max > control.min ? control.max : validBounds ? control.min + 1 : 1} step={control.step > 0 ? control.step : .1} value={[rangeValue]} disabled={!validBounds || control.min === control.max} onValueChange={values => onChange(control.key, String(values[0]))} />
        <div className="student-lab-range-labels"><Text size="1" color="gray">{control.min}</Text><Text size="1" color="gray">{control.max} {control.unit}</Text></div>
        {invalid && <Text as="p" size="1" color="red" className="student-lab-error">Nhập giá trị từ {control.min} đến {control.max} {control.unit}.</Text>}
      </div>;
    })}</div>
    {error && <Text as="p" size="2" color="red" role="alert">{error}</Text>}
  </section>;
}