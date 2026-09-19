import { ResourceDiscovery } from "../../features/library/components/ResourceDiscovery";
import { useCommunityLibrary } from "../../features/library/hooks/useCommunityLibrary";
import PageContainer from "../../shared/layout/PageContainer";
import "../../styles/assignment-flow.css";

export default function CommunityLibrary() {
  const library = useCommunityLibrary();
  return (
    <PageContainer className="community-library-page">
      <ResourceDiscovery
      {...library}
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
