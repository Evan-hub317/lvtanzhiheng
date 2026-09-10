import request from '@/utils/request'

export const saveMessageApi = data => request.post('/chat/messages', data)

export const sessionListApi = () => request.get('/chat/sessions')

export const messageListApi = sessionId => request.get(`/chat/sessions/${sessionId}/messages`)

export const deleteSessionApi = sessionId => request.delete(`/chat/sessions/${sessionId}`)
