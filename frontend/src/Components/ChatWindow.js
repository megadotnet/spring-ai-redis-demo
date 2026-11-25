import React, {Component, createRef} from 'react';
import Button from 'react-bootstrap/Button'
import Badge from 'react-bootstrap/Badge';
import Form from 'react-bootstrap/Form';
import {ChatBubble} from './ChatBubble';
import { SendMessage, SendAgentMessage, StartChat, StartAgentSession, FetchAgentHistory, FetchNativeHistory } from '../api';

// 同一个聊天组件服务两条通道，差别只在“调哪个接口 + 展示多少中间产物”。
// native = RagController 的 /chat/{chatId}（legacy 回滚路径）
// agentic = AgentChatController 的 /api/agent/chat（带证据、步骤、traceId 与引用校验）
const MODES = {
  native: {
    title: 'Native RAG',
    endpoint: 'POST /chat/{chatId}',
    tagVariant: 'secondary',
    pendingText: 'awaiting response'
  },
  agentic: {
    title: 'Agentic RAG',
    endpoint: 'POST /api/agent/chat',
    tagVariant: 'warning',
    pendingText: '规划与检索中，可能需要数十秒…'
  }
};

const truncate = (text, maxChars) => {
  if(!text){
    return '';
  }
  return text.length > maxChars ? `${text.slice(0, maxChars)}…` : text;
};

// 两条通道都把会话 ID 持久到本地（各自独立 key，互不残留），
// 刷新/重进页面时恢复同一会话并拉回历史；服务端共用 rag:session: 记忆，
// 同一 ID 在两条通道间回切时历史互通。
const SESSION_STORAGE_KEYS = {
  native: 'rag:native-session-id',
  agentic: 'rag:agentic-session-id'
};

export class ChatWindow extends Component{
  static displayName = ChatWindow.name;
  constructor(props){
    super(props)
    // 默认走 agentic，保持不带 prop 直接渲染时的既有行为
    this.mode = props.mode === 'native' ? 'native' : 'agentic';
    this.state = {messages:[], input:'', chatId:'', awaitingServer: true, textAreaRows: 1, historyLoading: false}  
    this.fileRef = createRef();
    // 同步追踪当前会话 ID：setState 在 async 链路里可能延迟生效，
    // 用 React state 做历史加载守卫会误判把结果丢弃
    this.activeSessionId = '';
  }

  async componentDidMount(){    
    await this.start();
  }

  modeInfo = () => MODES[this.mode];

  sendMessage = async () => {
    if(this.state.input.trim() === '' || this.uiDisabled()){
      // 输入框在 pending 时已禁用，但 Enter 仍会走到这里；Agentic 通道一轮要几十秒，
      // 不加这道闸门会并发打出多条检索请求。
      return;
    }
    const question = this.state.input;
    this.setState((prev) => ({
      messages:[...prev.messages, {message:question, userType:'user'}],
      input:'',
      textAreaRows: 1,
      messagePending: true
    }));

    let reply;
    try{
      reply = this.mode === 'agentic'
        ? await this.sendAgentMessage(question)
        : await this.sendNativeMessage(question);
    }
    catch(e){
      // native 通道的 SendMessage 会直接抛出（超时、非 JSON 响应体等），这里兜住避免整页崩掉
      reply = {message:`请求失败：${(e && e.message) || e}`, userType:'system'};
    }

    this.setState((prev) => ({messagePending:false, messages:[...prev.messages, reply]}));
  }

  sendAgentMessage = async (question) => {
    const response = await SendAgentMessage(question, this.state.chatId);
    return {
      message: response.message,
      userType: response.ok ? 'bot' : 'system',
      // 只有拿到真正的 AgentChatResponse 才挂 meta：请求失败时 grounded 恒为 false、evidence 恒为空，
      // 无条件渲染会把一条 503/断网错误气泡说成“引用校验未通过”，误导成模型的答案不可信
      meta: response.ok
        ? {
            traceId: response.traceId,
            grounded: response.grounded,
            evidence: response.evidence,
            plan: response.plan
          }
        : null
    };
  }

  sendNativeMessage = async (question) => {
    const response = await SendMessage(question, this.state.chatId);
    return {
      message: response && response.message ? response.message : '服务端未返回回答内容。',
      userType: 'bot'
    };
  }

  uiDisabled = () =>{
    // 历史加载期间同样 gating 发送：此时 chatId 已就绪但列表还未恢复，
    // 放行会造成新提问排在未加载完的历史前面、顺序错乱
    return this.state.messagePending || this.state.historyLoading;
  }

  readSavedSession = () => {
    try{
      return window.localStorage.getItem(SESSION_STORAGE_KEYS[this.mode]) || '';
    }
    catch(e){
      // 隐私模式等场景 localStorage 不可用：退回“每次新建会话”，不影响本页可用
      return '';
    }
  }

  saveSession = (chatId) => {
    try{
      window.localStorage.setItem(SESSION_STORAGE_KEYS[this.mode], chatId);
    }
    catch(e){
      // 存不下就只影响下次恢复，当前会话照常可用
    }
  }

  // forceNew=true 时（点“新会话”）丢弃本地保存的 session，重新申请一条；
  // 两条通道同策略：有存档先恢复，无存档才调各自 startChat 新建并落盘
  acquireSession = async (forceNew) => {
    const saved = forceNew ? '' : this.readSavedSession();
    if(saved){
      // 恢复旧会话：服务端无历史/TTL 过期都会正常返回空列表，不会报错
      return saved;
    }
    const chatId = this.mode === 'agentic'
      ? await StartAgentSession()
      : (await StartChat()).chatId;
    this.saveSession(chatId);
    return chatId;
  }

  start = async (forceNew) => {
    try{
      const chatId = await this.acquireSession(forceNew === true);
      this.activeSessionId = chatId;
      this.setState({messages:[], input:'', textAreaRows: 1, chatId: chatId, messagePending: false, awaitingServer: false})
      await this.loadHistory(chatId);
    }
    catch(e){
      this.activeSessionId = '';
      this.setState({messages:[], input:'', chatId:'', messagePending:false, awaitingServer: true})
      setTimeout(() => this.start(), 5000);
    }    
  }

  // 历史加载失败只弹友好提示，不阻塞页面；后端未持久化 meta，故历史消息不带元信息
  loadHistory = async (sessionId) => {
    this.setState({historyLoading: true});
    const result = this.mode === 'agentic'
      ? await FetchAgentHistory(sessionId)
      : await FetchNativeHistory(sessionId);
    // 加载期间用户可能已点“新会话”切走，旧结果直接丢弃
    if(this.activeSessionId !== sessionId){
      return;
    }
    if(result.ok){
      this.setState({historyLoading: false, messages: result.messages.map(this.toHistoryMessage)})
    }
    else{
      this.setState((prev) => ({
        historyLoading: false,
        messages:[...prev.messages, {message:`历史加载失败：${result.error} 您可以继续直接提问。`, userType:'system'}]
      }))
    }
  }

  toHistoryMessage = (item) => ({
    message: item.content,
    userType: item.role === 'user' ? 'user' : 'bot'
  })

  handleKeyPress = (event)=>{
    if (event.key === 'Enter') {
      this.sendMessage();
    }
  };

  // 表单默认提交会重新加载页面并清空消息列表，这里接管成只发一次请求
  handleFormSubmit = (event) => {
    event.preventDefault();
    this.sendMessage();
  };

  calculateRows = (text) => {
    const lines = text.split('\n');
    let numLines = 0;
    if(lines){
      lines.forEach(element => {
        numLines += 1
        numLines += element.length / 65      
      });
    }
    
    return Math.min(Math.max(numLines, 1), 5);
  };

  handleTextAreaChange = (e) => {
    const value = e.target.value;
    const rows = this.calculateRows(value);
    this.setState({input:value, textAreaRows: rows});
  } 

  renderHeader = () => {
    const info = this.modeInfo();
    return (
      <div style={{display:'flex', alignItems:'center', gap:'10px', padding:'8px 12px', backgroundColor:'#3f4052', flexWrap:'wrap'}}>
        <a href="#/" style={{color:'#9fd0ff', textDecoration:'none'}}>← 入口</a>
        <span style={{color:'white', fontWeight:600}}>{info.title}</span>
        <Badge bg={info.tagVariant}>{this.mode}</Badge>
        <code style={{color:'#a9b0c4', fontSize:'12px'}}>{info.endpoint}</code>
        <span style={{marginLeft:'auto', display:'flex', alignItems:'center', gap:'10px'}}>
          {
            this.state.chatId &&
            (<span style={{color:'#7d8399', fontSize:'12px'}}>session {this.state.chatId.slice(0, 8)}</span>)
          }
          <Button
            size="sm"
            variant="outline-light"
            disabled={this.uiDisabled()}
            onClick={() => this.start(true)}
          >
            新会话
          </Button>
        </span>
      </div>
    );
  }

  // 只有 agentic 回复带 meta：把引用校验结果、执行步骤和证据展开成可读的调试面板
  renderMeta = (meta) => {
    if(!meta){
      return null;
    }
    const evidence = meta.evidence || [];
    const plan = meta.plan || [];
    // 合成单个文本节点，不拆成多段，方便测试和屏幕阅读器读出一行完整状态
    const summary = `${meta.grounded ? '引用校验通过' : '引用校验未通过'} · 证据 ${evidence.length} 条 · traceId ${meta.traceId || '无'}`;
    return (
      <details style={{margin:'0 4px 10px 4px', maxWidth:'60%', color:'#c9cbd8', fontSize:'12px'}}>
        <summary style={{cursor:'pointer'}}>{summary}</summary>
        <div style={{marginTop:'4px'}}>
          <div>步骤：{plan.length ? plan.join(' → ') : '无'}</div>
          {
            evidence.map((item, index) => (
              <div key={index} style={{marginTop:'6px', borderLeft:'2px solid #6c7086', paddingLeft:'8px'}}>
                <div>
                  [{index + 1}] {item.source || item.documentId || '未知来源'}
                  {item.page ? ` · 第 ${item.page} 页` : ''}
                  {item.retrievalSource ? ` · ${item.retrievalSource}` : ''}
                  {item.score !== undefined && item.score !== null ? ` · score ${Number(item.score).toFixed(4)}` : ''}
                </div>
                <div style={{whiteSpace:'pre-wrap', color:'#a9b0c4'}}>{truncate(item.content, 300)}</div>
              </div>
            ))
          }
        </div>
      </details>
    );
  }

  render(){
    if(this.state.awaitingServer){
      return(
        <div style={{display:'flex', justifyContent:'center', alignItems:'center', height: '100vh'}}>
          <span>Awaiting Server to start up...</span>
        </div>
      )
    }   


    return(
    <div style={{display: 'flex', flexDirection: 'column', height: '100vh', width: '100vw', backgroundColor: '#525365'}}>
      {this.renderHeader()}
      <div style={{ flexGrow:1, overflowY: 'auto', padding: '10px', display: 'flex', flexDirection: 'column' }}>
        {this.state.messages.map((msg, index) => (
            <React.Fragment key={index}>
              <ChatBubble message={msg.message} userType={msg.userType}/>
              {this.renderMeta(msg.meta)}
            </React.Fragment>
        ))}
        {
          this.state.historyLoading &&
            (<ChatBubble message="正在加载对话历史…" userType="system"/>)
        }
        {
          this.state.messagePending  && 
            (<ChatBubble message={this.modeInfo().pendingText} userType="system"/>)          
        }
      </div>

      <Form onSubmit={this.handleFormSubmit} style={{display: 'flex', justifyContent: 'center', marginBottom: '10px'}}>
        <Form.Group className='mb-2' > 
          <Form.Label style={{color: 'white'}}>Ask a question</Form.Label>          
          <div style={{display: 'flex', width: '100%'}}>
            <Form.Control              
              {... (this.uiDisabled() ? {disabled:'disabled'} : {})}
              onKeyUp={this.handleKeyPress}
              as={'textarea'} 
              rows={this.state.textAreaRows} 
              cols={60}
              value={this.state.input}
              onChange={(e) => this.handleTextAreaChange(e)}
              style={{margin: '5px', flexGrow: 1, maxWidth: '600px', borderRadius: '10px', overflowWrap: 'break-word', overflowX: 'hidden', overflowY: 'auto'}}
              />
            <Button type='submit' style={{height: 'auto', alignSelf: 'stretch'}} disabled={this.uiDisabled()}>Send</Button>
          </div>
          
        </Form.Group>
        
      </Form>
    </div>
    );
  }
}
