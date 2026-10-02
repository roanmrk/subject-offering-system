import React from 'react';
import GhostFibers from './GhostFibers';

const PageBackground = ({ variant = 'dark' }) => {
  const isDark = variant === 'dark';
  const fallbackColor = isDark ? '#0a0a0a' : '#fcf9f2';

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 0,
        pointerEvents: 'none',
        backgroundColor: fallbackColor,
        background: fallbackColor,
        width: '100vw',
        height: '100vh',
      }}
    >
      <GhostFibers
        lineColor={isDark ? '#1E3A5F' : '#1a1a1a'}
        glowColor={isDark ? '#0A1828' : '#c9c4b8'}
        speed={0.15}
        scale={2}
        rotation={0}
        rotationSpeed={0.15}
        layers={isDark ? 4 : 3}
        waveAmplitude={0.015}
        waveFrequency={3}
        waveSpeed={0.12}
        layerSpeed={0.06}
        twist={0.1}
        twistFrequency={5}
        twistSpeed={1.0}
        lineFrequency={5}
        lineSpacing={2}
        lineSharpness={16}
        glowFalloff={10}
        glowIntensity={isDark ? 0.6 : 0.8}
        brightness={isDark ? 1.2 : 1.6}
        blueBoost={isDark ? 1.4 : 1.0}
        vignette={isDark ? 0.9 : 0.7}
        grain={0.05}
        dpr={1}
        fps={45}
        lightMode={!isDark}
      />
    </div>
  );
};

export default PageBackground;