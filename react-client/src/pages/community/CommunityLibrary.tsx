import { ResourceDiscovery } from "../../features/library/components/ResourceDiscovery";
import { useCommunityLibrary } from "../../features/library/hooks/useCommunityLibrary";
import PageContainer from "../../shared/layout/PageContainer";
import styles from "./CommunityLibrary.module.css";
import "../../styles/assignment-flow.css";
import { usePhysliveStore } from "../../store/usePhysliveStore";

export default function CommunityLibrary() {
  const library = useCommunityLibrary();
  const user = usePhysliveStore(state => state.user);
  return (
    <PageContainer className="community-library-page">
      <header className={styles.heading}><h1>Kho cộng đồng</h1></header>
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
