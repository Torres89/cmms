import { useState, useRef, useEffect, FC, KeyboardEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Fab,
  Paper,
  Box,
  Typography,
  TextField,
  IconButton,
  Chip,
  CircularProgress,
  useTheme,
  alpha,
  Zoom
} from '@mui/material';
import SmartToyTwoToneIcon from '@mui/icons-material/SmartToyTwoTone';
import CloseTwoToneIcon from '@mui/icons-material/CloseTwoTone';
import SendTwoToneIcon from '@mui/icons-material/SendTwoTone';
import OpenInNewTwoToneIcon from '@mui/icons-material/OpenInNewTwoTone';
import { agentUrl } from 'src/config';
import { v4 as uuidv4 } from 'uuid';

interface ChatLink {
  label: string;
  url: string;
}

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  links?: ChatLink[];
}

const ChatBubble: FC = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      role: 'assistant',
      content:
        "Hi! I'm your Atlas CMMS assistant. Ask me anything — create work orders, look up assets, manage inventory, and more."
    }
  ]);
  const [loading, setLoading] = useState(false);
  const [sessionId] = useState(() => uuidv4());
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async () => {
    const trimmed = input.trim();
    if (!trimmed || loading) return;

    const userMsg: ChatMessage = { role: 'user', content: trimmed };
    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setLoading(true);

    try {
      const res = await fetch(`${agentUrl}/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: trimmed, session_id: sessionId })
      });

      if (!res.ok) {
        throw new Error(`Server error: ${res.status}`);
      }

      const data = await res.json();
      const assistantMsg: ChatMessage = {
        role: 'assistant',
        content: data.reply || 'No response.',
        links: data.links || []
      };
      setMessages((prev) => [...prev, assistantMsg]);
    } catch (err: any) {
      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          content: `⚠️ Could not reach the agent server. Make sure it's running on ${agentUrl}.`
        }
      ]);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleLinkClick = (url: string) => {
    navigate(url);
    setOpen(false);
  };

  return (
    <>
      {/* Floating action button */}
      <Zoom in={!open}>
        <Fab
          id="chat-bubble-fab"
          color="primary"
          onClick={() => setOpen(true)}
          sx={{
            position: 'fixed',
            bottom: 24,
            right: 24,
            zIndex: 1400,
            width: 60,
            height: 60,
            boxShadow: `0 4px 20px ${alpha(theme.colors.primary.main, 0.4)}`
          }}
        >
          <SmartToyTwoToneIcon sx={{ fontSize: 28 }} />
        </Fab>
      </Zoom>

      {/* Chat panel */}
      <Zoom in={open}>
        <Paper
          id="chat-bubble-panel"
          elevation={12}
          sx={{
            position: 'fixed',
            bottom: 24,
            right: 24,
            zIndex: 1400,
            width: 380,
            height: 520,
            display: 'flex',
            flexDirection: 'column',
            borderRadius: 3,
            overflow: 'hidden',
            border: `1px solid ${alpha(theme.colors.primary.main, 0.2)}`
          }}
        >
          {/* Header */}
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              px: 2,
              py: 1.5,
              background: `linear-gradient(135deg, ${theme.colors.primary.main}, ${theme.colors.primary.dark})`,
              color: theme.palette.primary.contrastText
            }}
          >
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <SmartToyTwoToneIcon sx={{ fontSize: 22 }} />
              <Typography variant="h6" sx={{ fontWeight: 600, fontSize: 15 }}>
                Atlas Assistant
              </Typography>
            </Box>
            <IconButton
              size="small"
              onClick={() => setOpen(false)}
              sx={{ color: 'inherit' }}
            >
              <CloseTwoToneIcon fontSize="small" />
            </IconButton>
          </Box>

          {/* Messages */}
          <Box
            sx={{
              flex: 1,
              overflowY: 'auto',
              px: 2,
              py: 1.5,
              display: 'flex',
              flexDirection: 'column',
              gap: 1.5,
              bgcolor:
                theme.palette.mode === 'dark'
                  ? alpha(theme.colors.alpha.black[100], 0.3)
                  : theme.colors.alpha.white[100]
            }}
          >
            {messages.map((msg, idx) => (
              <Box
                key={idx}
                sx={{
                  display: 'flex',
                  justifyContent:
                    msg.role === 'user' ? 'flex-end' : 'flex-start'
                }}
              >
                <Box
                  sx={{
                    maxWidth: '85%',
                    px: 2,
                    py: 1,
                    borderRadius: 2,
                    bgcolor:
                      msg.role === 'user'
                        ? theme.colors.primary.main
                        : theme.palette.mode === 'dark'
                        ? alpha(theme.colors.alpha.white[100], 0.08)
                        : alpha(theme.colors.alpha.black[100], 0.06),
                    color:
                      msg.role === 'user'
                        ? theme.palette.primary.contrastText
                        : theme.palette.text.primary
                  }}
                >
                  <Typography
                    variant="body2"
                    sx={{
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-word',
                      fontSize: 13,
                      lineHeight: 1.5
                    }}
                  >
                    {msg.content}
                  </Typography>
                  {msg.links && msg.links.length > 0 && (
                    <Box
                      sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5, mt: 1 }}
                    >
                      {msg.links.map((link, i) => (
                        <Chip
                          key={i}
                          label={link.label}
                          size="small"
                          icon={<OpenInNewTwoToneIcon sx={{ fontSize: 14 }} />}
                          onClick={() => handleLinkClick(link.url)}
                          sx={{
                            cursor: 'pointer',
                            fontWeight: 500,
                            fontSize: 11
                          }}
                          color="primary"
                          variant="outlined"
                        />
                      ))}
                    </Box>
                  )}
                </Box>
              </Box>
            ))}
            {loading && (
              <Box sx={{ display: 'flex', justifyContent: 'flex-start' }}>
                <Box
                  sx={{
                    px: 2,
                    py: 1.5,
                    borderRadius: 2,
                    bgcolor:
                      theme.palette.mode === 'dark'
                        ? alpha(theme.colors.alpha.white[100], 0.08)
                        : alpha(theme.colors.alpha.black[100], 0.06)
                  }}
                >
                  <CircularProgress size={18} />
                </Box>
              </Box>
            )}
            <div ref={messagesEndRef} />
          </Box>

          {/* Input */}
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              px: 1.5,
              py: 1,
              borderTop: `1px solid ${theme.colors.alpha.black[10]}`
            }}
          >
            <TextField
              id="chat-input"
              fullWidth
              size="small"
              placeholder="Type a message..."
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={loading}
              sx={{
                '& .MuiOutlinedInput-root': {
                  borderRadius: 2,
                  fontSize: 13
                }
              }}
            />
            <IconButton
              id="chat-send-btn"
              color="primary"
              onClick={handleSend}
              disabled={!input.trim() || loading}
              size="small"
            >
              <SendTwoToneIcon fontSize="small" />
            </IconButton>
          </Box>
        </Paper>
      </Zoom>
    </>
  );
};

export default ChatBubble;
