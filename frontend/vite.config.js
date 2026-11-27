import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import { createApiProxy, createApiProxyLogger } from './config/apiProxy.js'

const projectDirectory = fileURLToPath(new URL('.', import.meta.url))

export default defineConfig(({ mode }) => {
  // DEV_ is server-side configuration. Keep Vite's client envPrefix unchanged.
  const env = loadEnv(mode, projectDirectory, 'DEV_')
  const target = env.DEV_API_PROXY_TARGET
  return {
    root: projectDirectory,
    envDir: projectDirectory,
    plugins: [react()],
    customLogger: createApiProxyLogger(),
    server: { host: '127.0.0.1', proxy: createApiProxy(target) },
    preview: { host: '127.0.0.1', proxy: createApiProxy(target) },
  }
})
