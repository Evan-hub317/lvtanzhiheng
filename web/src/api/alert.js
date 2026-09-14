import request from '@/utils/request'

export const alertScanApi = () => request.post('/alert/scan')

export const alertAnomalyApi = () => request.post('/alert/anomaly', null, { timeout: 300000 })

export const alertPageApi = params => request.get('/alert/page', { params })

export const alertHandleApi = id => request.put(`/alert/${id}/handle`)

export const alertSummaryApi = () => request.get('/alert/summary')
