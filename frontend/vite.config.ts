import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const proxy = { '/api': `http://127.0.0.1:${process.env.SERVER_PORT || 8080}` }

export default defineConfig({
  plugins: [react()],
  server: { host: '127.0.0.1', port: Number(process.env.ROOTME_FRONTEND_PORT || 5173), strictPort: true, proxy },
  preview: { host: '127.0.0.1', port: 4173, strictPort: true, proxy },
})
