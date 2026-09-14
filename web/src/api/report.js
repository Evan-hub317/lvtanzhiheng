import request from '@/utils/request'

export const generateReportApi = data => request.post('/report/generate', data, { timeout: 180000 })

export const reportListApi = () => request.get('/report/list')

export const reportDetailApi = id => request.get(`/report/${id}`)

export const regenerateReportApi = id => request.post(`/report/${id}/regenerate`, null, { timeout: 180000 })

export const deleteReportApi = id => request.delete(`/report/${id}`)
