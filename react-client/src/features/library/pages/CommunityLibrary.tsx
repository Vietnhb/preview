import { useMemo } from "react";
import { ResourceDiscovery } from "../components/ResourceDiscovery";
import { useCommunityLibrary } from "../hooks/useCommunityLibrary";
import PageContainer from "../../../shared/layout/PageContainer";
import DotField from "../../../shared/effects/DotField";
import { SplitWords } from "../../../shared/effects/Motion";
import CountUp from "../../../shared/effects/reactbits/CountUp";
import styles from "./CommunityLibrary.module.css";
import "../../assignments/styles/assignment-flow.css";
import { useSessionStore } from "../../../shared/auth/sessionStore";

export default function CommunityLibrary() {
  const library = useCommunityLibrary();
  const user = useSessionStore(state => state.user);
  // Real numbers from what is currently shared; nothing here is a marketing figure.
  const stats = useMemo(() => [
    { label: "mô phỏng", value: library.items.length },
    { label: "giáo viên chia sẻ", value: new Set(library.items.map(item => item.sharedById ?? item.sharedByName).filter(Boolean)).size },
    { label: "bài học có mô phỏng", value: new Set(library.items.map(item => item.lessonId).filter(Boolean)).size },
  ], [library.items]);
  return (
    <PageContainer className={`community-library-page ${styles.page}`}>
      <header className={styles.hero}>
        <DotField gap={22} radius={120} activeColor="var(--accent)" />
        <div className={styles.heroCopy}>
          <h1><SplitWords text="Kho cộng đồng" stagger={0.06} /></h1>
          <p>Mô phỏng Vật lý do giáo viên chia sẻ, đã qua kiểm duyệt. Chọn theo lớp và bài học, mở ra là chạy thử được ngay.</p>
        </div>
        {(library.loading || library.items.length > 0) && <dl className={styles.stats}>
          {stats.map(stat => <div key={stat.label}><dd>{library.loading ? "—" : <CountUp to={stat.value} duration={1.2} />}</dd><dt>{stat.label}</dt></div>)}
        </dl>}
      </header>
      <ResourceDiscovery
        {...library}
        allowSchoolScope={Boolean(user)}
        onRetry={() => void library.load()}
        vectors={{
          grid: true,
          trajectory: true,
          velocity: true,
          acceleration: false,
        }}
        onOpen={(item) => void library.open(item)}
        onClose={library.close}
        onTogglePlaying={library.togglePlaying}
        onReset={library.reset}
        onFrameChange={library.seek}
        onTimeChange={library.updateTime}
        onPlaybackEnd={library.stop}
      />
    </PageContainer>
  );
}
