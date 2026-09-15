type IconName =
  | "atom"
  | "back"
  | "login"
  | "logout"
  | "menu"
  | "play"
  | "pause"
  | "reset"
  | "step"
  | "chart"
  | "sliders"
  | "book"
  | "grid"
  | "arrow"
  | "check"
  | "download"
  | "upload"
  | "camera"
  | "file"
  | "bulb"
  | "close"
  | "settings"
  | "panel"
  | "view2d"
  | "view3d"
  | "fullscreen"
  | "sun"
  | "moon"
  | "system"
  | "folder"
  | "folderPlus"
  | "plus"
  | "search"
  | "users"
  | "message"
  | "activity"
  | "shield"
  | "refresh";

const paths: Record<IconName, string> = {
  atom: "M12 3c4 0 7 4 7 9s-3 9-7 9-7-4-7-9 3-9 7-9 M3 8c2-3 7-3 11-1s8 6 6 9-7 3-11 1S1 11 3 8 M3 16c-2-3 2-7 6-9s9-2 11 1-2 7-6 9-9 2-11-1",
  back: "M19 12H5m6-6-6 6 6 6",
  login: "m10 17 5-5-5-5M15 12H3M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4",
  logout: "m16 17 5-5-5-5M21 12H9M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4",
  menu: "M4 6h16M4 12h16M4 18h16",
  play: "m8 5 11 7-11 7Z",
  pause: "M8 5v14M16 5v14",
  reset: "M4 10a8 8 0 1 1 1 8M4 4v6h6",
  step: "m5 5 10 7-10 7ZM19 5v14",
  chart: "M4 4v16h16M7 16l4-5 4 2 5-7",
  sliders: "M5 3v8m0 4v6M12 3v3m0 4v11M19 3v11m0 4v3M2 11h6M9 6h6M16 14h6",
  book: "M12 5v15M12 5C9 3 5 3 3 4v15c3-1 6-1 9 1 3-2 6-2 9-1V4c-2-1-6-1-9 1",
  grid: "M4 4h6v6H4ZM14 4h6v6h-6ZM4 14h6v6H4ZM14 14h6v6h-6Z",
  arrow: "M5 12h14m-6-6 6 6-6 6",
  check: "m5 12 4 4L19 6",
  download: "M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5",
  upload: "M12 16V4m0 0L7 9m5-5 5 5M5 20h14",
  camera: "M4 7h3l1.5-2h7L17 7h3a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2Zm8 3.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7Z",
  file: "M6 3h8l4 4v14H6zM14 3v5h5",
  bulb: "M9 18h6m-5 3h4M8 15a7 7 0 1 1 8 0l-1 3H9Z",
  close: "m6 6 12 12M6 18 18 6",
  settings:
    "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
  panel: "M4 5h16v14H4zM9 5v14",
  view2d: "M4 5h16v14H4zM4 12h16M12 5v14",
  view3d: "m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Zm0 9 8-4.5M12 12v9M4 7.5 12 12",
  fullscreen: "M8 3H3v5M16 3h5v5M8 21H3v-5M21 16v5h-5",
  sun: "M12 3v2m0 14v2M3 12h2m14 0h2M5.64 5.64l1.42 1.42m9.88 9.88 1.42 1.42M18.36 5.64l-1.42 1.42m-9.88 9.88-1.42 1.42M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10Z",
  moon: "M20 15.5A8.5 8.5 0 0 1 8.5 4 8.5 8.5 0 1 0 20 15.5Z",
  system: "M4 5h16v11H4zM8 20h8M12 16v4",
  folder:
    "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
  folderPlus:
    "M12 10v6M9 13h6M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
  plus: "M5 12h14M12 5v14",
  search: "m21 21-4.35-4.35M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z",
  users: "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm7-7.5a4 4 0 0 1 0 7.75M22 21v-2a4 4 0 0 0-3-3.87",
  message: "M21 11.5a8.5 8.5 0 0 1-9 8.5 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.7A8.4 8.4 0 0 1 4 11.5 8.5 8.5 0 0 1 12.5 3h.5a8.5 8.5 0 0 1 8 8v.5Z",
  activity: "M3 12h4l3-8 4 16 3-8h4",
  shield: "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z",
  refresh: "M20 11a8 8 0 1 0 2 5m0 0v-5m0 5h-5",
};

export default function LearningIcon({ name }: { name: IconName }) {
  return (
    <svg
      className="learn-icon"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={name === "settings" ? 1.5 : 1.7}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
      {name === "settings" && <circle cx="12" cy="12" r="3" />}
    </svg>
  );
}
