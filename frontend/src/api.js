export const SendMessage = async function(message, chatId){
    const responseMessage = await fetch(`chat/${chatId}`,{
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            prompt:message
        })
    });

    return responseMessage.json();
}

// 后端错误体可能是 Spring 默认结构，也可能是 RagController 的 {message}，取不到就退回状态码文案。
const readErrorMessage = function(data, status){
    if(data && typeof data.message === 'string' && data.message){
        return data.message;
    }
    if(data && typeof data.error === 'string' && data.error){
        return data.error;
    }
    return `Agent 请求失败（HTTP ${status}）`;
}

const safeJson = async function(response){
    try{
        return await response.json();
    }
    catch(e){
        return null;
    }
}

/**
 * 灰度通道：对应 AgentChatController 的 POST /api/agent/chat。
 * 请求体是 AgentChatRequest{sessionId, question, topK}，响应体是
 * AgentChatResponse{traceId, answer, evidence, plan, grounded}。
 * 这里补齐 message 字段，使 ChatWindow 现有读取方式（response.message）无需改动即可切换。
 *
 * @param {string} question 用户问题；后端为空时会直接抛 IllegalArgumentException，所以前端先拦一层
 * @param {string} sessionId 会话 ID，复用 StartChat 返回的 chatId 即可
 * @param {{topK?: number, signal?: AbortSignal}} [options] topK 由后端收敛到 1~20；signal 可传 AbortSignal.timeout(ms)
 * @returns {Promise<{ok: boolean, status: number, traceId: string, message: string, answer: string,
 *                    evidence: Array, plan: Array, grounded: boolean, error?: string}>}
 *          失败时不抛出，而是返回 ok=false + 可读文案，调用方按 message 渲染即可。
 */
export const SendAgentMessage = async function(question, sessionId, options = {}){
    if(!question || String(question).trim() === ''){
        const errorText = '问题不能为空。';
        return {ok:false, status:0, traceId:'', message:errorText, answer:errorText, evidence:[], plan:[], grounded:false, error:errorText};
    }

    const payload = {
        sessionId: sessionId,
        question: String(question).trim()
    };
    if(options.topK !== undefined && options.topK !== null){
        payload.topK = options.topK;
    }

    try{
        const response = await fetch('api/agent/chat',{
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(payload),
            signal: options.signal
        });

        const data = await safeJson(response);

        if(!response.ok){
            const errorText = readErrorMessage(data, response.status);
            return {
                ok: false,
                status: response.status,
                traceId: data && data.traceId ? data.traceId : '',
                message: errorText,
                answer: errorText,
                evidence: [],
                plan: [],
                grounded: false,
                error: errorText
            };
        }

        const answer = data && data.answer ? data.answer : '服务端未返回回答内容。';
        return {
            ok: true,
            status: response.status,
            traceId: data && data.traceId ? data.traceId : '',
            message: answer,
            answer: answer,
            evidence: data && Array.isArray(data.evidence) ? data.evidence : [],
            plan: data && Array.isArray(data.plan) ? data.plan : [],
            grounded: Boolean(data && data.grounded)
        };
    }
    catch(e){
        // 网络中断 / 主动取消：返回文案而非抛出，行为与 legacy 调用方保持一致。
        const errorText = (e && e.name === 'AbortError')
            ? '请求已取消。'
            : `无法连接 Agent 服务：${(e && e.message) || e}`;
        return {ok:false, status:0, traceId:'', message:errorText, answer:errorText, evidence:[], plan:[], grounded:false, error:errorText};
    }
}

/**
 * Agent 会话初始化。AgentChatController 没有独立的 start 接口，
 * 会话 ID 沿用 RagController 的 POST /chat/startChat 返回的 chatId，
 * 这样两条通道共用同一份 Redis 短期记忆 key，回切 legacy 时历史不丢。
 * 后端未就绪时这里会抛出，由调用方（ChatWindow）沿用原有的“等待服务端启动”重试；
 * 不在前端造 UUID 兜底，否则会生成服务端查不到历史、又掩盖了宕机的孤儿会话。
 */
export const StartAgentSession = async function(){
    const response = await fetch('chat/startChat',{
        headers: {
            'Content-Type': 'application/json'
        },
        method: 'POST'
    });
    if(!response.ok){
        throw new Error(`startChat 返回 HTTP ${response.status}`);
    }
    const data = await safeJson(response);
    if(!data || !data.chatId){
        throw new Error('startChat 未返回 chatId');
    }
    return data.chatId;
}

/**
 * 拉取 Agent 会话历史，对应 AgentChatController 的 GET /api/agent/history/{sessionId}。
 * 服务端只持久化 role/content（traceId、evidence、plan、grounded 未落库），
 * 这里只返回可恢复的文本消息，不伪造元信息。
 * 与 SendAgentMessage 同风格：失败不抛出，返回 ok=false + 可读 error，
 * 调用方（ChatWindow）据此展示友好提示并继续可用。
 *
 * @param {string} sessionId 会话 ID
 * @returns {Promise<{ok: boolean, status: number, messages: Array<{role: string, content: string}>, error?: string}>}
 */
export const FetchAgentHistory = async function(sessionId){
    if(!sessionId || String(sessionId).trim() === ''){
        return {ok:false, status:0, messages:[], error:'缺少会话 ID，无法加载历史。'};
    }

    try{
        const response = await fetch(`api/agent/history/${encodeURIComponent(String(sessionId).trim())}`);
        const data = await safeJson(response);

        if(!response.ok){
            const errorText = readErrorMessage(data, response.status);
            return {ok:false, status:response.status, messages:[], error:errorText};
        }

        // 非法/意外响应体（非 JSON、messages 不是数组）一律当空历史处理，不阻断页面
        const raw = data && Array.isArray(data.messages) ? data.messages : [];
        const messages = raw
            .filter(item => item && typeof item.content === 'string' && item.content !== '')
            .map(item => ({
                role: item.role === 'user' ? 'user' : 'assistant',
                content: item.content
            }));
        return {ok:true, status:response.status, messages};
    }
    catch(e){
        const errorText = (e && e.name === 'AbortError')
            ? '历史加载已取消。'
            : `无法连接历史接口：${(e && e.message) || e}`;
        return {ok:false, status:0, messages:[], error:errorText};
    }
}

export const StartChat = async function(){
    const responseMessage = await fetch("chat/startChat", {
        headers: {
            'Content-Type': 'application/json'
        },
        method: 'POST'    
    })       
    return responseMessage.json();
}