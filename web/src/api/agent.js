import request from '@/utils/request'

export const agentSessionListApi = () => request.get('/agent/sessions')

export const agentSessionDetailApi = id => request.get(`/agent/sessions/${id}`)

export const agentDeleteSessionApi = id => request.delete(`/agent/sessions/${id}`)
