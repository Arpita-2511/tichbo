/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        bg: {
          primary: '#0a0a0f',
          secondary: '#12121a',
          tertiary: '#1a1a27',
        },
        border: {
          DEFAULT: '#1e1e2e',
          light: '#2a2a3e',
        },
        accent: {
          DEFAULT: '#7c3aed',
          light: '#8b5cf6',
          lighter: '#a78bfa',
          dark: '#6d28d9',
        },
        text: {
          primary: '#f1f5f9',
          secondary: '#94a3b8',
          muted: '#64748b',
        },
        success: '#10b981',
        warning: '#f59e0b',
        error: '#ef4444',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      backgroundImage: {
        'hero-gradient': 'linear-gradient(to bottom, rgba(10,10,15,0) 0%, rgba(10,10,15,0.6) 60%, rgba(10,10,15,1) 100%)',
        'card-gradient': 'linear-gradient(to top, rgba(10,10,15,0.95) 0%, rgba(10,10,15,0.5) 50%, transparent 100%)',
        'accent-gradient': 'linear-gradient(135deg, #7c3aed 0%, #8b5cf6 100%)',
      },
      animation: {
        'fade-in': 'fadeIn 0.3s ease-in-out',
        'slide-up': 'slideUp 0.3s ease-out',
        'skeleton': 'skeleton 1.5s ease-in-out infinite',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        slideUp: {
          '0%': { transform: 'translateY(16px)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
        skeleton: {
          '0%, 100%': { opacity: '0.4' },
          '50%': { opacity: '0.8' },
        },
      },
    },
  },
  plugins: [],
}

