import { render, screen } from '@testing-library/react';
import { act } from 'react-dom/test-utils';
import userEvent from '@testing-library/user-event';
import App from './App';

// 入口页/聊天页挂载时都会申请会话，这里把 api 层整体 mock 掉，避免测试依赖 8080 后端。
// 注意用普通 async 函数而不是 jest.fn(impl)：jest.mock 工厂的返回值会经过 metadata 序列化，
// 传给 jest.fn 的实现会被丢弃，调用只剩返回 undefined。
// 变量必须以 mock 开头，否则 babel-plugin-jest-hoist 会拒绝工厂引用外部作用域。
let mockAgentResponse = {
  ok: true,
  status: 200,
  traceId: 'trace-1',
  message: 'agent answer',
  answer: 'agent answer',
  evidence: [{
    documentId: 'doc-1',
    content: '混合检索将向量召回与 BM25 关键词召回融合后交给 Rerank。',
    source: 'Architecture.md',
    page: 2,
    retrievalSource: 'hybrid',
    score: 0.81234
  }],
  plan: ['hybrid_retrieve', 'generate_answer', 'grounding_check_passed'],
  grounded: true
};

// 默认空历史：进入页面时恢复会话但不渲染历史气泡，保持存量用例行为不变
let mockHistoryResponse = {ok: true, status: 200, messages: []};

jest.mock('./api', () => ({
  SendMessage: async () => ({message: 'native answer'}),
  SendAgentMessage: async () => mockAgentResponse,
  StartChat: async () => ({chatId: 'chat-1'}),
  StartAgentSession: async () => 'chat-1',
  FetchAgentHistory: async () => mockHistoryResponse,
  FetchNativeHistory: async () => mockHistoryResponse
}));

afterEach(() => {
  window.location.hash = '';
  window.localStorage.clear();
});

// 进入 agentic 页并提问；输入与发送都是 async 链路，包在 act 里避免 React 报“更新未被 act 包裹”
const askAgent = async () => {
  window.location.hash = '#/agentic';
  render(<App />);
  const input = await screen.findByRole('textbox');
  await act(async () => {
    userEvent.type(input, '什么是混合检索');
  });
  await act(async () => {
    userEvent.click(screen.getByRole('button', {name: 'Send'}));
  });
};

test('entry page lists both RAG channels', () => {
  render(<App />);
  expect(screen.getByText('Native RAG')).toBeInTheDocument();
  expect(screen.getByText('Agentic RAG')).toBeInTheDocument();
  expect(screen.getByRole('link', {name: '进入 Native RAG 问答页面'})).toHaveAttribute('href', '#/native');
  expect(screen.getByRole('link', {name: '进入 Agentic RAG 问答页面'})).toHaveAttribute('href', '#/agentic');
});

test('#/native routes to the legacy chat page', async () => {
  window.location.hash = '#/native';
  render(<App />);
  expect(await screen.findByText('Native RAG')).toBeInTheDocument();
  expect(screen.getByText('POST /chat/{chatId}')).toBeInTheDocument();
  expect(screen.queryByRole('heading', {name: 'Spring AI Redis RAG Demo'})).not.toBeInTheDocument();
});

test('#/agentic routes to the agent chat page', async () => {
  window.location.hash = '#/agentic';
  render(<App />);
  expect(await screen.findByText('Agentic RAG')).toBeInTheDocument();
  expect(screen.getByText('POST /api/agent/chat')).toBeInTheDocument();
});

test('agentic answer shows grounded status and evidence under the bubble', async () => {
  await askAgent();

  expect(await screen.findByText('agent answer')).toBeInTheDocument();
  expect(screen.getByText('引用校验通过 · 证据 1 条 · traceId trace-1')).toBeInTheDocument();
  // 展开区里能看到步骤与单条证据的来源、页码、检索来源、分数和正文
  expect(screen.getByText('步骤：hybrid_retrieve → generate_answer → grounding_check_passed')).toBeInTheDocument();
  expect(screen.getByText(/\[1\] Architecture\.md · 第 2 页 · hybrid · score 0\.8123/)).toBeInTheDocument();
  expect(screen.getByText(/混合检索将向量召回与 BM25 关键词召回融合后交给 Rerank。/)).toBeInTheDocument();
});

test('agent refusal without evidence still shows an empty evidence panel', async () => {
  mockAgentResponse = {
    ok: true,
    status: 200,
    traceId: 'trace-2',
    message: '当前知识库中未检索到足够的相关资料，无法可靠回答。',
    answer: '当前知识库中未检索到足够的相关资料，无法可靠回答。',
    evidence: [],
    plan: ['hybrid_retrieve', 'generate_answer'],
    grounded: false
  };
  try{
    await askAgent();

    expect(await screen.findByText('当前知识库中未检索到足够的相关资料，无法可靠回答。')).toBeInTheDocument();
    expect(screen.getByText('引用校验未通过 · 证据 0 条 · traceId trace-2')).toBeInTheDocument();
  }
  finally{
    mockAgentResponse = {
      ok: true, status: 200, traceId: 'trace-1', message: 'agent answer', answer: 'agent answer',
      evidence: [], plan: [], grounded: true
    };
  }
});

test('failed agent request does not pretend to be a grounding failure', async () => {
  mockAgentResponse = {
    ok: false,
    status: 0,
    traceId: '',
    message: '无法连接 Agent 服务：Failed to fetch',
    answer: '无法连接 Agent 服务：Failed to fetch',
    evidence: [],
    plan: [],
    grounded: false
  };
  try{
    await askAgent();

    expect(await screen.findByText('无法连接 Agent 服务：Failed to fetch')).toBeInTheDocument();
    expect(screen.queryByText(/引用校验/)).not.toBeInTheDocument();
  }
  finally{
    mockAgentResponse = {
      ok: true, status: 200, traceId: 'trace-1', message: 'agent answer', answer: 'agent answer',
      evidence: [], plan: [], grounded: true
    };
  }
});

test('agentic mount restores history bubbles in chronological order', async () => {
  mockHistoryResponse = {
    ok: true,
    status: 200,
    messages: [
      {role: 'user', content: '历史提问：什么是混合检索'},
      {role: 'assistant', content: '历史回答：向量与 BM25 融合召回'}
    ]
  };
  try{
    window.location.hash = '#/agentic';
    render(<App />);
    expect(await screen.findByText('历史提问：什么是混合检索')).toBeInTheDocument();
    expect(screen.getByText('历史回答：向量与 BM25 融合召回')).toBeInTheDocument();
    // 加载完成后不应残留“正在加载”状态，也不伪造元信息面板
    expect(screen.queryByText('正在加载对话历史…')).not.toBeInTheDocument();
    expect(screen.queryByText(/引用校验/)).not.toBeInTheDocument();
  }
  finally{
    mockHistoryResponse = {ok: true, status: 200, messages: []};
  }
});

test('native mount restores history bubbles without agent meta', async () => {
  // 方案 A：native 通道也持久化 chatId 并调 GET /chat/history/{chatId} 恢复；
  // native 回答无 meta，历史渲染不应出现引用校验面板
  mockHistoryResponse = {
    ok: true,
    status: 200,
    messages: [
      {role: 'user', content: 'native 历史提问'},
      {role: 'assistant', content: 'native 历史回答'}
    ]
  };
  try{
    window.location.hash = '#/native';
    render(<App />);
    expect(await screen.findByText('native 历史提问')).toBeInTheDocument();
    expect(screen.getByText('native 历史回答')).toBeInTheDocument();
    expect(screen.queryByText('正在加载对话历史…')).not.toBeInTheDocument();
    expect(screen.queryByText(/引用校验/)).not.toBeInTheDocument();
  }
  finally{
    mockHistoryResponse = {ok: true, status: 200, messages: []};
  }
});

test('history load failure shows a friendly hint and keeps the page usable', async () => {
  mockHistoryResponse = {ok: false, status: 500, messages: [], error: '会话历史读取失败，请稍后重试'};
  try{
    window.location.hash = '#/agentic';
    render(<App />);
    expect(await screen.findByText(/历史加载失败：会话历史读取失败/)).toBeInTheDocument();
    const input = await screen.findByRole('textbox');
    expect(input).not.toBeDisabled();
    expect(screen.getByRole('button', {name: 'Send'})).not.toBeDisabled();
  }
  finally{
    mockHistoryResponse = {ok: true, status: 200, messages: []};
  }
});
