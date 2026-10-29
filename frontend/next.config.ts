import type { NextConfig } from 'next';

const config: NextConfig = {
  output: 'export',
  poweredByHeader: false,
  trailingSlash: true,
  images: { unoptimized: true },
};
export default config;
