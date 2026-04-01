import type { Config } from 'tailwindcss'

const config: Config = {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: {
          primary: '#0A0A0F',
          secondary: '#12121A',
          tertiary: '#1A1A26',
        },
        text: {
          primary: '#E8E6F0',
          secondary: '#9B97AD',
          muted: '#5C586E',
        },
        accent: {
          blue: '#6C8EFF',
          purple: '#A78BFA',
          green: '#34D399',
        },
        status: {
          parsing: '#F59E0B',
          indexed: '#34D399',
          failed: '#EF4444',
          uploading: '#6C8EFF',
        },
        citation: {
          bg: '#1E1B2E',
          border: '#3B3558',
          hover: '#2A2640',
        },
      },
      fontFamily: {
        heading: ['Bricolage Grotesque', 'sans-serif'],
        body: ['IBM Plex Sans', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
      fontSize: {
        hero: '3rem',
        h1: '2.25rem',
        h2: '1.5rem',
        body: '1rem',
        caption: '0.8125rem',
        code: '0.875rem',
      },
      borderRadius: {
        sm: '6px',
        md: '10px',
        lg: '16px',
        pill: '9999px',
      },
    },
  },
  plugins: [],
}

export default config
