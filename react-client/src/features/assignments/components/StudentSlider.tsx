import type { ComponentPropsWithoutRef } from "react";
import { Slider as SliderPrimitive } from "radix-ui";

type Props = Omit<ComponentPropsWithoutRef<typeof SliderPrimitive.Root>, "children"> & {
  label: string;
  size?: "1" | "2" | "3";
};

/** Theme styling with an explicit accessible name on each Radix slider thumb. */
export function StudentSlider({ label, size = "2", className = "", ...props }: Readonly<Props>) {
  const values = props.value ?? props.defaultValue ?? [0];
  return <SliderPrimitive.Root {...props} className={`rt-SliderRoot rt-r-size-${size} rt-variant-surface ${className}`} data-accent-color="indigo">
    <SliderPrimitive.Track className="rt-SliderTrack"><SliderPrimitive.Range className="rt-SliderRange" /></SliderPrimitive.Track>
    {values.map((_, index) => <SliderPrimitive.Thumb key={index} className="rt-SliderThumb" aria-label={label} />)}
  </SliderPrimitive.Root>;
}