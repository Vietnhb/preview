import type { LicensePlan } from "../../api/authApi";
import { motion, useReducedMotion } from "motion/react";
import s from "./PlanCard.module.css";

const money = (value: number) =>
  new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
    maximumFractionDigits: 0,
  }).format(value);

const count = (value: number) => value.toLocaleString("vi-VN");

type PlanCardProps = {
  plan: LicensePlan;
  name: string;
  selected: boolean;
  disabled?: boolean;
  current?: boolean;
  onSelect: (code: string) => void;
};

export default function PlanCard({
  plan,
  name,
  selected,
  disabled = false,
  current = false,
  onSelect,
}: PlanCardProps) {
  const reduced = useReducedMotion();
  return (
    <motion.label
      className={s.card}
      data-selected={selected}
      data-disabled={disabled}
      layout
      whileHover={reduced || disabled ? undefined : { y: -4 }}
      whileTap={reduced || disabled ? undefined : { scale: 0.985 }}
      transition={{ type: "spring", stiffness: 400, damping: 32 }}
    >
      <div className={s.heading}>
        <div>
          <span className={s.kicker}>
            {current ? "GÓI HIỆN TẠI" : "GÓI NĂM"}
          </span>
          <h3>{plan.name}</h3>
        </div>
        <input
          type="radio"
          name={name}
          value={plan.code}
          checked={selected}
          disabled={disabled}
          onChange={() => onSelect(plan.code)}
        />
        {selected && (
          <motion.span
            className={s.selectionMarker}
            layoutId={`${name}-selection`}
            aria-hidden="true"
          />
        )}
      </div>
      <p className={s.description}>{plan.description}</p>
      <div className={s.price}>
        <strong>{money(plan.annualPriceVnd)}</strong>
        <span>/ năm</span>
      </div>
      <dl className={s.facts}>
        <div>
          <dt>Học sinh</dt>
          <dd>{count(plan.studentQuota)}</dd>
        </div>
        <div>
          <dt>Token AI / tháng</dt>
          <dd>
            {plan.monthlyTokenQuota === null
              ? "Không giới hạn"
              : count(plan.monthlyTokenQuota)}
          </dd>
        </div>
      </dl>
      <ul className={s.benefits}>
        <li>
          <span aria-hidden="true">+</span>Giáo viên và lớp học không giới hạn
        </li>
        <li>
          <span aria-hidden="true">+</span>Học sinh chạy mô phỏng miễn phí
        </li>
      </ul>
      <span className={s.select}>
        {selected ? "Đã chọn gói" : "Chọn gói này"}
      </span>
    </motion.label>
  );
}
