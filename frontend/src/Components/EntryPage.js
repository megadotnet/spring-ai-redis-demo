import React from 'react';
import Card from 'react-bootstrap/Card';
import Badge from 'react-bootstrap/Badge';

// 入口页只做通道选择：hash 跳转，不引路由库，也不在这里碰后端接口。
// 两条通道共用 /chat/startChat 分配的会话 ID，区别只在生成答案走哪个 Controller。
const ENTRIES = [
  {
    mode: 'native',
    hash: '#/native',
    title: 'Native RAG',
    tag: 'legacy',
    tagVariant: 'secondary',
    endpoint: 'POST /chat/{chatId}',
    summary: '一次性检索增强问答',
    details: '向量 + BM25 混合检索后直接交给 LLM 生成，没有规划、重检索和引用校验环节。保留为稳定回滚路径。'
  },
  {
    mode: 'agentic',
    hash: '#/agentic',
    title: 'Agentic RAG',
    tag: 'beta',
    tagVariant: 'warning',
    endpoint: 'POST /api/agent/chat',
    summary: '有界 Agent Runtime 问答',
    details: '规则规划 → 至多 3 次混合检索 → 上下文预算与去重 → 生成 → 引用校验；回答附带证据、执行步骤与 traceId，证据不足时稳定拒答。'
  }
];

export function EntryPage(){
  return (
    <main style={{
      minHeight: '100vh',
      width: '100vw',
      backgroundColor: '#525365',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '24px'
    }}>
      <h1 style={{color: 'white', marginBottom: '6px'}}>Spring AI Redis RAG Demo</h1>
      <p style={{color: '#c9cbd8', marginBottom: '28px'}}>选择一种问答通道进入</p>

      <nav aria-label="问答通道" style={{display: 'flex', gap: '20px', flexWrap: 'wrap', justifyContent: 'center'}}>
        {ENTRIES.map((entry) => (
          <Card key={entry.mode} style={{width: '340px', backgroundColor: '#3f4052', border: 'none'}}>
            <Card.Body style={{display: 'flex', flexDirection: 'column'}}>
              <Card.Title style={{color: 'white', display: 'flex', alignItems: 'center', gap: '8px'}}>
                {entry.title}
                <Badge bg={entry.tagVariant}>{entry.tag}</Badge>
              </Card.Title>
              <Card.Subtitle style={{color: '#9fd0ff', marginBottom: '10px'}}>{entry.summary}</Card.Subtitle>
              <Card.Text style={{color: '#c9cbd8', fontSize: '14px', minHeight: '84px'}}>{entry.details}</Card.Text>
              <code style={{color: '#a9b0c4', fontSize: '12px'}}>{entry.endpoint}</code>
              <div style={{marginTop: '16px'}}>
                <a
                  className="btn btn-primary"
                  href={entry.hash}
                  aria-label={`进入 ${entry.title} 问答页面`}
                >
                  进入
                </a>
              </div>
            </Card.Body>
          </Card>
        ))}
      </nav>

      <p style={{color: '#7d8399', fontSize: '12px', marginTop: '28px'}}>
        灰度期间两条通道并行，可随时返回此页切换。
      </p>
    </main>
  );
}

export default EntryPage;
