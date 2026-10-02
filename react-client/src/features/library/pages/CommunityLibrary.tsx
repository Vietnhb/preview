import { ResourceDiscovery } from "../components/ResourceDiscovery";
import { useCommunityLibrary } from "../hooks/useCommunityLibrary";
import PageContainer from "../../../shared/layout/PageContainer";
import styles from "./CommunityLibrary.module.css";
import "../../assignments/styles/assignment-flow.css";
import { useSessionStore } from "../../../shared/auth/sessionStore";

export default function CommunityLibrary() {
  const library = useCommunityLibrary();
  const user = useSessionStore(state => state.user);
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