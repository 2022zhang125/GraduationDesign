import { createRouter, createWebHashHistory } from "vue-router";
import { clearToken, getToken } from "../api";
import MusicHomeLayout from "../layouts/MusicHomeLayout.vue";
import FavoritesView from "../views/FavoritesView.vue";
import FeedbackView from "../views/FeedbackView.vue";
import FriendsView from "../views/FriendsView.vue";
import HistoryView from "../views/HistoryView.vue";
import LibraryView from "../views/LibraryView.vue";
import LoginView from "../views/LoginView.vue";
import OverviewView from "../views/OverviewView.vue";
import RecommendationsView from "../views/RecommendationsView.vue";
import RegisterView from "../views/RegisterView.vue";
import SearchView from "../views/SearchView.vue";
import { isAdminUser, resolveCurrentUserId } from "../utils/session";

const routes = [
  {
    path: "/",
    redirect: () => (getToken() ? "/home/overview" : "/login")
  },
  {
    path: "/login",
    name: "login",
    component: LoginView
  },
  {
    path: "/register",
    name: "register",
    component: RegisterView
  },
  {
    path: "/logout",
    name: "logout",
    beforeEnter: () => {
      clearToken();
      return { name: "login" };
    }
  },
  {
    path: "/home",
    component: MusicHomeLayout,
    children: [
      {
        path: "",
        redirect: { name: "home-overview" }
      },
      {
        path: "overview",
        name: "home-overview",
        component: OverviewView
      },
      {
        path: "library",
        name: "home-library",
        component: LibraryView,
        meta: { adminOnly: true }
      },
      {
        path: "search/:userId?",
        name: "home-search",
        component: SearchView,
        props: (route) => ({
          userId: route.params.userId || route.query.userId || ""
        })
      },
      {
        path: "recommendations/:userId?",
        name: "home-recommendations",
        component: RecommendationsView,
        props: (route) => ({
          userId: route.params.userId || route.query.userId || ""
        })
      },
      {
        path: "favorites/:userId?",
        name: "home-favorites",
        component: FavoritesView,
        props: (route) => ({
          userId: route.params.userId || route.query.userId || ""
        })
      },
      {
        path: "history/:userId?",
        name: "home-history",
        component: HistoryView,
        props: (route) => ({
          userId: route.params.userId || route.query.userId || ""
        })
      },
      {
        path: "feedback/:userId?",
        name: "home-feedback",
        component: FeedbackView,
        props: (route) => ({
          userId: route.params.userId || route.query.userId || ""
        })
      },
      {
        path: "friends/:userId?",
        name: "home-friends",
        component: FriendsView,
        props: (route) => ({
          userId: route.params.userId || route.query.userId || ""
        })
      }
    ]
  },
  {
    path: "/:pathMatch(.*)*",
    redirect: "/"
  }
];

const router = createRouter({
  history: createWebHashHistory(),
  routes
});

const publicRouteNames = new Set(["login", "register", "logout"]);
const userScopedRouteNames = new Set([
  "home-search",
  "home-recommendations",
  "home-favorites",
  "home-history",
  "home-friends",
  "home-feedback"
]);

router.beforeEach((to) => {
  const hasToken = Boolean(getToken());

  if (!publicRouteNames.has(to.name) && !hasToken) {
    return {
      name: "login",
      query: {
        redirect: to.fullPath
      }
    };
  }

  if ((to.name === "login" || to.name === "register") && hasToken) {
    return { name: "home-overview" };
  }

  const admin = isAdminUser();
  if (to.meta?.adminOnly && !admin) {
    return { name: "home-overview" };
  }

  if (!admin && userScopedRouteNames.has(to.name)) {
    const currentUserId = resolveCurrentUserId();
    if (!currentUserId) {
      return { name: "login" };
    }

    const routeUserId = String(to.params.userId || "");
    if (routeUserId !== currentUserId) {
      return {
        name: to.name,
        params: { userId: currentUserId },
        query: { ...to.query }
      };
    }
  }

  return true;
});

export default router;
