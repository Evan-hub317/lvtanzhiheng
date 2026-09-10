import request from '@/utils/request'

export const executeCalcApi = data => request.post('/calc/execute', data, { timeout: 180000 })
