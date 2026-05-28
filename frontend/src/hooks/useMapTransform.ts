import { useEffect, useState, type RefObject } from 'react';
import { MAP_WIDTH, MAP_HEIGHT } from '../data/campaignStages';

export interface MapTransform {
  scale: number;
  offsetX: number;
  offsetY: number;
  viewportWidth: number;
  viewportHeight: number;
}

const MAP_ASPECT = MAP_WIDTH / MAP_HEIGHT;

function compute(width: number, height: number): MapTransform {
  const viewportAspect = width / height;

  let scale: number;
  let offsetX: number;
  let offsetY: number;

  if (viewportAspect > MAP_ASPECT) {
    scale = width / MAP_WIDTH;
    offsetX = 0;
    offsetY = (height - MAP_HEIGHT * scale) / 2;
  } else {
    scale = height / MAP_HEIGHT;
    offsetX = (width - MAP_WIDTH * scale) / 2;
    offsetY = 0;
  }

  return {
    scale,
    offsetX,
    offsetY,
    viewportWidth: width,
    viewportHeight: height,
  };
}

export function useMapTransform(
  containerRef: RefObject<HTMLElement | null>,
): MapTransform {
  const [transform, setTransform] = useState<MapTransform>(() =>
    compute(window.innerWidth, window.innerHeight),
  );

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    const update = () => {
      const rect = el.getBoundingClientRect();
      setTransform(compute(rect.width, rect.height));
    };

    update();

    const observer = new ResizeObserver(update);
    observer.observe(el);

    return () => observer.disconnect();
  }, [containerRef]);

  return transform;
}
