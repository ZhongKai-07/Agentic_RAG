import type { Config } from 'tailwindcss'

const config: Config = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: {
          primary: '#fdfbf7',
          secondary: '#ffffff',
          tertiary: '#f5f0e8',
        },
        text: {
          primary: '#2d2d2d',
          secondary: '#5a5a5a',
          muted: '#8a8a8a',
        },
        accent: {
          blue: '#ff4d4d',
          purple: '#2d5da1',
          green: '#2d8a4e',
        },
        status: {
          parsing: '#e6a817',
          indexed: '#2d8a4e',
          failed: '#cc3333',
          uploading: '#ff4d4d',
        },
        citation: {
          bg: '#fff9e6',
          border: '#2d2d2d',
          hover: '#fff3cc',
        },
      },
      fontFamily: {
        heading: ['Kalam', 'cursive'],
        body: ['Patrick Hand', 'cursive'],
        mono: ['Patrick Hand', 'cursive'],
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
      boxShadow: {
        hard: '4px 4px 0px 0px #2d2d2d',
        'hard-sm': '2px 2px 0px 0px #2d2d2d',
        'hard-lg': '8px 8px 0px 0px #2d2d2d',
        'hard-hover': '6px 6px 0px 0px #2d2d2d',
      },
      keyframes: {
        jiggle: {
          '0%, 100%': { transform: 'rotate(-1deg)' },
          '50%': { transform: 'rotate(1deg)' },
        },
      },
      animation: {
        jiggle: 'jiggle 0.3s ease-in-out',
      },
    },
  },
  plugins: [],
}

export default config
