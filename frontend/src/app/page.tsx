import { CreatorShell } from '../components/creator-shell';
import { PublicLanding } from '../components/public-landing';

export default function Home() {
  return (
    <CreatorShell publicPage>
      <PublicLanding />
    </CreatorShell>
  );
}
