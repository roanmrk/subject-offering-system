import React from 'react';
import GhostFibers from './GhostFibers';

/**
 * Reusable page background using GhostFibers.
 *
 * variant="dark"  → subtle blue fibers on very dark backdrop (Landing, Login)
 * variant="light" → dark fibers on light backdrop (Dashboards)
 */
const PageBackground = ({ variant = 'dark' }) => {
  const isDark = variant === 'dark';

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 0,
        pointerEvents: 'none',
        // Pure dark backdrop
        background: isDark ? '#000000' : 'transparent',
      }}
    >
      <GhostFibers
        lineColor={isDark ? '#1E3A5F' : '#1a1a1a'}       // very dark blue
        glowColor={isDark ? '#0A1828' : '#c9c4b8'}        // near-black blue
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
        glowIntensity={isDark ? 0.6 : 0.8}                // ↓ dimmer
        brightness={isDark ? 1.2 : 1.6}                   // ↓ darker
        blueBoost={isDark ? 1.4 : 1.0}                    // keep blue tint
        vignette={isDark ? 0.9 : 0.7}                     // ↓ darker edges
        grain={0.05}
        dpr={1}
        fps={45}
        lightMode={!isDark}
      />
    </div>
  );
};

export default PageBackground;