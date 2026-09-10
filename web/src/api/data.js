import request from '@/utils/request'

// 批量生成 10 万+ 条数据耗时较长，单独加长超时时间
export const generateDataApi = data => request.post('/data/generate', data, { timeout: 180000 })

export const dataStatusApi = () => request.get('/data/status')
