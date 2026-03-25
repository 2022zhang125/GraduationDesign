<script setup>
import { onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  closeFeedbackTicket,
  createFeedbackTicket,
  fetchFeedbackTickets
} from "../api";
import { isAdminUser, resolveCurrentUserId, resolveCurrentUsername } from "../utils/session";

const text = {
  adminTitle: "用户反馈工单",
  userTitle: "我的反馈工单",
  helper: "提交工单后，后端会把你对曲风、歌手、语言的增强或减少偏好加入 NCF + Attention 打分，并立即刷新每日推荐。",
  refresh: "刷新列表",
  inputUserKey: "输入用户 ID 或用户名",
  statusAll: "全部状态",
  statusActive: "进行中",
  statusClosed: "已关闭",
  sectionCreate: "新建反馈工单",
  preferDimension: "增强维度",
  preferValue: "增强目标",
  avoidDimension: "减少维度",
  avoidValue: "减少目标",
  strength: "影响强度",
  detail: "工单说明",
  detailPlaceholder: "例如：最近 POP 歌曲太多，请适当降低 POP 权重，多推一些许嵩和华语民谣。",
  valuePlaceholderGenre: "例如：POP / ROCK / FOLK",
  valuePlaceholderArtist: "例如：许嵩 / 陈奕迅",
  valuePlaceholderLanguage: "例如：zh / en / jp",
  submit: "提交工单",
  ticketId: "工单 ID",
  username: "用户名",
  preferColumn: "增强倾向",
  avoidColumn: "减少倾向",
  strengthColumn: "强度",
  detailColumn: "说明",
  statusColumn: "状态",
  timeColumn: "提交时间",
  action: "操作",
  close: "关闭工单",
  created: "工单已提交，新的偏好信号已纳入推荐计算。",
  closed: "工单已关闭，对应反馈权重已取消。",
  countUnit: "条",
  needUserAdmin: "请先输入用户 ID 或用户名。",
  needUserSelf: "未获取到当前用户信息，请重新登录。",
  emptyAdmin: "请输入用户 ID 或用户名后查看对应的反馈工单。",
  emptyUser: "你还没有提交反馈工单，可以直接告诉系统你想增强或减少哪类音乐。",
  dimensionGenre: "曲风",
  dimensionArtist: "歌手",
  dimensionLanguage: "语言",
  tendencyPrefer: "增强",
  tendencyAvoid: "减少",
  noDetail: "无",
  noPreference: "未填写"
};

const dimensionOptions = [
  { value: "", label: "不设置" },
  { value: "GENRE", label: text.dimensionGenre },
  { value: "ARTIST", label: text.dimensionArtist },
  { value: "LANGUAGE", label: text.dimensionLanguage }
];

const statusOptions = [
  { value: "", label: text.statusAll },
  { value: "ACTIVE", label: text.statusActive },
  { value: "CLOSED", label: text.statusClosed }
];

const strengthOptions = [1, 2, 3, 4, 5];

const createEmptyForm = () => ({
  preferDimension: "",
  preferValue: "",
  avoidDimension: "GENRE",
  avoidValue: "",
  preferenceStrength: 3,
  detailText: ""
});

const props = defineProps({
  userId: {
    type: [String, Number],
    default: ""
  }
});

const route = useRoute();
const router = useRouter();
const admin = isAdminUser(resolveCurrentUsername());
const selfUserId = resolveCurrentUserId();

const selectedUserId = ref(props.userId ? String(props.userId) : selfUserId);
const statusFilter = ref("ACTIVE");
const tickets = ref([]);
const total = ref(0);
const busy = ref(false);
const error = ref("");
const success = ref("");
const form = ref(createEmptyForm());

const syncSelectedUserId = (value) => {
  if (admin) {
    selectedUserId.value = value ? String(value) : "";
    return;
  }
  selectedUserId.value = selfUserId;
};

const formatDateTime = (value) => {
  if (!value) {
    return "-";
  }
  return String(value).replace("T", " ").replace(/\.\d+$/, "");
};

const formatStatus = (value) => {
  if (value === "CLOSED") {
    return text.statusClosed;
  }
  return text.statusActive;
};

const formatDimension = (dimension) => {
  if (dimension === "GENRE") {
    return text.dimensionGenre;
  }
  if (dimension === "ARTIST") {
    return text.dimensionArtist;
  }
  if (dimension === "LANGUAGE") {
    return text.dimensionLanguage;
  }
  return "";
};

const describeTendency = (dimension, value, prefix) => {
  if (!value) {
    return text.noPreference;
  }
  const label = formatDimension(dimension);
  return `${prefix}${label ? `${label} ` : ""}${value}`;
};

const valuePlaceholder = (dimension) => {
  if (dimension === "ARTIST") {
    return text.valuePlaceholderArtist;
  }
  if (dimension === "LANGUAGE") {
    return text.valuePlaceholderLanguage;
  }
  return text.valuePlaceholderGenre;
};

const loadTickets = async () => {
  if (!selectedUserId.value) {
    error.value = admin ? text.needUserAdmin : text.needUserSelf;
    tickets.value = [];
    total.value = 0;
    return;
  }

  busy.value = true;
  error.value = "";
  success.value = "";
  try {
    const page = await fetchFeedbackTickets(selectedUserId.value, 1, 20, statusFilter.value);
    tickets.value = Array.isArray(page?.list) ? page.list : [];
    total.value = Number(page?.total || 0);
    if (admin) {
      router.replace({
        name: "home-feedback",
        params: { userId: selectedUserId.value },
        query: { ...route.query }
      });
    }
  } catch (e) {
    tickets.value = [];
    total.value = 0;
    error.value = e.message;
  } finally {
    busy.value = false;
  }
};

const submitTicket = async () => {
  if (!selectedUserId.value) {
    error.value = admin ? text.needUserAdmin : text.needUserSelf;
    return;
  }

  busy.value = true;
  error.value = "";
  success.value = "";
  try {
    await createFeedbackTicket(selectedUserId.value, {
      preferDimension: form.value.preferDimension,
      preferValue: form.value.preferValue,
      avoidDimension: form.value.avoidDimension,
      avoidValue: form.value.avoidValue,
      preferenceStrength: Number(form.value.preferenceStrength || 3),
      detailText: form.value.detailText
    });
    form.value = createEmptyForm();
    success.value = text.created;
    await loadTickets();
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
};

const closeTicketRow = async (row) => {
  busy.value = true;
  error.value = "";
  success.value = "";
  try {
    await closeFeedbackTicket(selectedUserId.value, row.ticketId);
    success.value = text.closed;
    await loadTickets();
  } catch (e) {
    error.value = e.message;
  } finally {
    busy.value = false;
  }
};

watch(
  () => props.userId,
  async (value) => {
    syncSelectedUserId(value);
    if (selectedUserId.value) {
      await loadTickets();
    }
  }
);

watch(statusFilter, async () => {
  if (selectedUserId.value) {
    await loadTickets();
  }
});

onMounted(async () => {
  syncSelectedUserId(props.userId);
  if (selectedUserId.value) {
    await loadTickets();
  }
});
</script>

<template>
  <div class="page-grid">
    <div class="section-head">
      <h2>{{ admin ? text.adminTitle : text.userTitle }}</h2>
      <button class="btn-secondary" :disabled="busy" type="button" @click="loadTickets">
        {{ text.refresh }}
      </button>
    </div>

    <p class="helper-text">{{ text.helper }}</p>

    <div class="inline-form">
      <input
        v-if="admin"
        v-model.trim="selectedUserId"
        :placeholder="text.inputUserKey"
      />
      <select v-model="statusFilter">
        <option v-for="option in statusOptions" :key="option.value || 'all'" :value="option.value">
          {{ option.label }}
        </option>
      </select>
    </div>

    <section class="panel feedback-form">
      <div class="section-head">
        <h3>{{ text.sectionCreate }}</h3>
        <span class="helper-text">{{ text.tendencyAvoid }} / {{ text.tendencyPrefer }} + NCF</span>
      </div>

      <div class="feedback-grid">
        <label class="feedback-field">
          <span class="field-label">{{ text.preferDimension }}</span>
          <select v-model="form.preferDimension">
            <option v-for="option in dimensionOptions" :key="`prefer-${option.value || 'none'}`" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>

        <label class="feedback-field">
          <span class="field-label">{{ text.preferValue }}</span>
          <input
            v-model.trim="form.preferValue"
            :placeholder="valuePlaceholder(form.preferDimension)"
          />
        </label>

        <label class="feedback-field">
          <span class="field-label">{{ text.avoidDimension }}</span>
          <select v-model="form.avoidDimension">
            <option v-for="option in dimensionOptions" :key="`avoid-${option.value || 'none'}`" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>

        <label class="feedback-field">
          <span class="field-label">{{ text.avoidValue }}</span>
          <input
            v-model.trim="form.avoidValue"
            :placeholder="valuePlaceholder(form.avoidDimension)"
          />
        </label>

        <label class="feedback-field">
          <span class="field-label">{{ text.strength }}</span>
          <select v-model.number="form.preferenceStrength">
            <option v-for="level in strengthOptions" :key="level" :value="level">
              {{ level }}
            </option>
          </select>
        </label>
      </div>

      <label class="feedback-field">
        <span class="field-label">{{ text.detail }}</span>
        <textarea
          v-model.trim="form.detailText"
          class="feedback-detail"
          :placeholder="text.detailPlaceholder"
        ></textarea>
      </label>

      <div class="inline-form">
        <button class="btn-primary" :disabled="busy" type="button" @click="submitTicket">
          {{ text.submit }}
        </button>
        <span class="helper-text">{{ total }} {{ text.countUnit }}</span>
      </div>
    </section>

    <p v-if="success" class="success-text">{{ success }}</p>
    <p v-if="error" class="error-text">{{ error }}</p>

    <table v-if="tickets.length" class="ncf-table">
      <thead>
        <tr>
          <th>{{ text.ticketId }}</th>
          <th>{{ text.username }}</th>
          <th>{{ text.preferColumn }}</th>
          <th>{{ text.avoidColumn }}</th>
          <th>{{ text.strengthColumn }}</th>
          <th>{{ text.detailColumn }}</th>
          <th>{{ text.statusColumn }}</th>
          <th>{{ text.timeColumn }}</th>
          <th>{{ text.action }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in tickets" :key="row.ticketId">
          <td>{{ row.ticketId }}</td>
          <td>{{ row.username || "-" }}</td>
          <td>{{ describeTendency(row.preferDimension, row.preferValue, text.tendencyPrefer) }}</td>
          <td>{{ describeTendency(row.avoidDimension, row.avoidValue, text.tendencyAvoid) }}</td>
          <td>{{ row.preferenceStrength || 3 }}</td>
          <td class="lyrics-snippet-cell">{{ row.detailText || text.noDetail }}</td>
          <td>{{ formatStatus(row.status) }}</td>
          <td>{{ formatDateTime(row.createdAt) }}</td>
          <td>
            <button
              v-if="row.status === 'ACTIVE'"
              class="btn-danger mini"
              type="button"
              @click="closeTicketRow(row)"
            >
              {{ text.close }}
            </button>
            <span v-else>-</span>
          </td>
        </tr>
      </tbody>
    </table>

    <section v-else class="panel panel-empty">
      <p class="helper-text">
        {{ admin ? text.emptyAdmin : text.emptyUser }}
      </p>
    </section>
  </div>
</template>