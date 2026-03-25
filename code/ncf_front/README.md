# ncf_front

Vue3 frontend for `ncf_back` with:

- login/register
- route guard by `vue-router`
- NCF recommendation rendering
- music preview
- favorites management

## Run

1. `cd D:\NCFResearch\code\ncf_front`
2. `npm install`
3. `npm run dev`

Dev URL: `http://localhost:5173`  
Proxy: `/api -> http://localhost:8081`

## Routes

- `/login`: login page
- `/register`: register page
- `/home/overview`: overview dashboard
- `/home/library`: users and items list
- `/home/recommendations/:userId?`: recommendation list + preview + favorite
- `/home/favorites/:userId?`: favorites list + preview

## API mapping

- `POST /api/auth/login`
- `POST /api/auth/register`
- `GET /api/stats/overview`
- `GET /api/users`
- `GET /api/items`
- `GET /api/recommendations/users/{userId}`
- `GET /api/recommendations/users/{userId}/items/{itemId}/explanation`
- `GET /api/media/items/{itemId}/preview`
- `GET /api/favorites/users/{userId}`
- `POST /api/favorites/users/{userId}/items/{itemId}`
- `DELETE /api/favorites/users/{userId}/items/{itemId}`
