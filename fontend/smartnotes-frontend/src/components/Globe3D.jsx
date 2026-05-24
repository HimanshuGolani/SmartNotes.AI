import React, { useRef, useMemo } from 'react';
import { Canvas, useFrame } from '@react-three/fiber';
import { Sphere, Stars, OrbitControls } from '@react-three/drei';
import * as THREE from 'three';

function AnimatedGlobe() {
  const meshRef = useRef();
  const wireframeRef = useRef();
  const glowRef = useRef();

  useFrame((state, delta) => {
    if (meshRef.current) {
      meshRef.current.rotation.y += delta * 0.15;
      meshRef.current.rotation.x = Math.sin(state.clock.elapsedTime * 0.3) * 0.1;
    }
    if (wireframeRef.current) {
      wireframeRef.current.rotation.y -= delta * 0.08;
      wireframeRef.current.rotation.z = Math.sin(state.clock.elapsedTime * 0.2) * 0.05;
    }
    if (glowRef.current) {
      const scale = 1 + Math.sin(state.clock.elapsedTime * 1.5) * 0.05;
      glowRef.current.scale.set(scale, scale, scale);
    }
  });

  return (
    <group>
      {/* Outer glow */}
      <Sphere ref={glowRef} args={[2.4, 32, 32]}>
        <meshBasicMaterial
          color="#7048e8"
          transparent
          opacity={0.08}
          side={THREE.BackSide}
        />
      </Sphere>

      {/* Main globe */}
      <Sphere ref={meshRef} args={[2, 64, 64]}>
        <meshStandardMaterial
          color="#1e293b"
          emissive="#7048e8"
          emissiveIntensity={0.3}
          roughness={0.4}
          metalness={0.6}
        />
      </Sphere>

      {/* Wireframe overlay */}
      <Sphere ref={wireframeRef} args={[2.05, 24, 24]}>
        <meshBasicMaterial
          color="#a78bfa"
          wireframe
          transparent
          opacity={0.25}
        />
      </Sphere>

      {/* Orbiting particles */}
      <OrbitingDots />
    </group>
  );
}

function OrbitingDots() {
  const groupRef = useRef();
  const dots = useMemo(() => {
    const arr = [];
    for (let i = 0; i < 30; i++) {
      const theta = Math.random() * Math.PI * 2;
      const phi = Math.acos(Math.random() * 2 - 1);
      const r = 2.6 + Math.random() * 0.4;
      arr.push({
        position: [
          r * Math.sin(phi) * Math.cos(theta),
          r * Math.cos(phi),
          r * Math.sin(phi) * Math.sin(theta),
        ],
        scale: 0.02 + Math.random() * 0.03,
      });
    }
    return arr;
  }, []);

  useFrame((state, delta) => {
    if (groupRef.current) {
      groupRef.current.rotation.y += delta * 0.1;
      groupRef.current.rotation.x += delta * 0.05;
    }
  });

  return (
    <group ref={groupRef}>
      {dots.map((dot, i) => (
        <mesh key={i} position={dot.position}>
          <sphereGeometry args={[dot.scale, 8, 8]} />
          <meshBasicMaterial color="#60a5fa" />
        </mesh>
      ))}
    </group>
  );
}

export default function Globe3D({ size = 400 }) {
  return (
    <div style={{ width: size, height: size }}>
      <Canvas camera={{ position: [0, 0, 7], fov: 50 }} dpr={[1, 2]}>
        <ambientLight intensity={0.3} />
        <pointLight position={[10, 10, 10]} intensity={1.5} color="#a78bfa" />
        <pointLight position={[-10, -10, -5]} intensity={0.8} color="#60a5fa" />
        <Stars radius={50} depth={50} count={1000} factor={3} fade speed={1} />
        <AnimatedGlobe />
        <OrbitControls
          enableZoom={false}
          enablePan={false}
          autoRotate
          autoRotateSpeed={0.5}
        />
      </Canvas>
    </div>
  );
}