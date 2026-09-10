import request from '@/utils/request'

export const loginApi = data => request.post('/auth/login', data)

export const getUserInfoApi = () => request.get('/auth/userInfo')

export const logoutApi = () => request.post('/auth/logout')
