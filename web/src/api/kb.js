import request from '@/utils/request'

export const kbListApi = () => request.get('/kb/list')

export const kbContentApi = id => request.get(`/kb/${id}/content`)

export const kbUploadApi = formData =>
  request.post('/kb/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 })

export const kbUpdateStatusApi = (id, status) => request.put(`/kb/${id}/status`, null, { params: { status } })

export const kbDeleteApi = id => request.delete(`/kb/${id}`)
