import request from '@/utils/request'

export const pageUsersApi = params => request.get('/user/page', { params })

export const createUserApi = data => request.post('/user', data)

export const updateUserApi = data => request.put('/user', data)

export const deleteUserApi = id => request.delete(`/user/${id}`)

export const resetPasswordApi = id => request.put(`/user/${id}/reset-password`)

export const changePasswordApi = data => request.put('/user/password', data)

export const updateProfileApi = realName =>
  request.put('/user/profile', null, { params: { realName } })
