import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({base:'/',plugins:[vue()],worker:{format:'es'},server:{host:'127.0.0.1',port:3001,proxy:{'/api':{target:process.env.VITE_API_PROXY||'http://localhost:8080',changeOrigin:true}}},resolve:{alias:{'@':'/src'}}})
