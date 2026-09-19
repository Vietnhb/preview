/** Debounce edits and ignore responses invalidated by another edit, reset or navigation. */
export function createSimulationAdjustment() {
  let revision = 0;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const cancel = () => {
    revision += 1;
    if (timer !== undefined) clearTimeout(timer);
    timer = undefined;
  };

  return {
    cancel,
    schedule<T>(request: () => Promise<T>, callbacks: {
      success: (result: T) => void;
      error: () => void;
      settled: () => void;
    }) {
      cancel();
      const current = revision;
      timer = setTimeout(async () => {
        timer = undefined;
        try {
          const result = await request();
          if (current === revision) callbacks.success(result);
        } catch {
          if (current === revision) callbacks.error();
        } finally {
          if (current === revision) callbacks.settled();
        }
      }, 180);
    },
  };
}
