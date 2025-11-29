import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [react()],
    base: './', // Use relative paths for GitHub Pages compatibility
    define: {
      // Polyfill process.env for the existing code structure
      'process.env': {
        API_KEY: JSON.stringify(env.API_KEY || process.env.API_KEY)
      }
    }
  }
})