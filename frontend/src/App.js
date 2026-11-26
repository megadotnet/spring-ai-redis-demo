import React, {useEffect, useState} from 'react';
import './App.css';
import { EntryPage } from './Components/EntryPage';
import { ChatWindow } from './Components/ChatWindow';
import 'bootstrap/dist/css/bootstrap.min.css';

// 用 hash 路由而不是 react-router：产物由 Spring Boot 静态目录托管时，
// /native 这类路径需要服务端 forward 配置才能刷新，而 #/native 不需要，也就不用多引一个依赖。
const HASH_TO_MODE = {
  '/native': 'native',
  '/agentic': 'agentic'
};

const readMode = () => HASH_TO_MODE[window.location.hash.replace(/^#/, '')] || null;

function App() {
  const [mode, setMode] = useState(readMode);

  useEffect(() => {
    const onHashChange = () => setMode(readMode());
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  if(!mode){
    return <EntryPage/>;
  }

  // key 保证切换通道时重挂载：各自申请一条会话，不把另一条通道的消息列表带过来
  return <ChatWindow key={mode} mode={mode}/>;
}

export default App;
