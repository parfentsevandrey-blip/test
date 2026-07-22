import "./index.css";
import { Composition } from "remotion";
import { Teaser } from "./Teaser";

export const RemotionRoot: React.FC = () => {
  return (
    <Composition
      id="k12-teaser"
      component={Teaser}
      durationInFrames={420}
      fps={30}
      width={1920}
      height={1080}
    />
  );
};
