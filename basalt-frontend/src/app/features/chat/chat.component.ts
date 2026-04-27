import {
  Component,
  ViewChild,
  ElementRef,
  AfterViewChecked,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { v4 as uuidv4 } from 'uuid';

import { ChatService } from '../../core/services/chat.service';
import { ChatMessage } from '../../core/models/chat.model';
import { MessageBubbleComponent } from './components/message-bubble/message-bubble.component';
import { ChatInputComponent, SendEvent } from './components/chat-input/chat-input.component';

/**
 * ChatComponent — the main conversation view.
 *
 * Orchestrates:
 * 1. Maintaining the ordered message history array.
 * 2. Dispatching user messages to ChatService (streaming chat or image gen).
 * 3. Streaming assistant token responses into a live message bubble.
 * 4. Auto-scrolling the viewport as new tokens arrive.
 */
@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, MessageBubbleComponent, ChatInputComponent],
  template: `
    <div class="flex flex-col h-screen bg-basalt-bg text-basalt-text font-sans">

      <!-- ── Header ─────────────────────────────────────────── -->
      <header class="flex items-center justify-between px-6 py-4 border-b border-basalt-border bg-basalt-surface flex-shrink-0">
        <div class="flex items-center gap-3">
          <!-- Basalt logo mark -->
          <div class="w-8 h-8 rounded-full bg-gradient-to-br from-basalt-accent to-purple-500 flex items-center justify-center font-bold text-basalt-bg text-sm">
            B
          </div>
          <h1 class="text-lg font-semibold text-basalt-text tracking-tight">Basalt</h1>
          <span class="text-xs text-basalt-muted bg-basalt-surface2 px-2 py-0.5 rounded-full">
            {{ modelLabel }}
          </span>
        </div>
        <div class="flex items-center gap-2 text-basalt-muted text-xs">
          <span class="w-2 h-2 rounded-full bg-green-400 inline-block"></span>
          Ollama connected
        </div>
      </header>

      <!-- ── Message List ───────────────────────────────────── -->
      <main
        #scrollContainer
        class="flex-1 overflow-y-auto py-6 scroll-smooth"
      >
        <!-- Empty state -->
        <div
          *ngIf="messages.length === 0"
          class="flex flex-col items-center justify-center h-full gap-4 text-center px-6"
        >
          <div class="w-16 h-16 rounded-full bg-gradient-to-br from-basalt-accent to-purple-500 flex items-center justify-center text-3xl font-bold text-basalt-bg">
            B
          </div>
          <h2 class="text-2xl font-semibold text-basalt-text">How can I help you today?</h2>
          <p class="text-basalt-muted max-w-md text-sm">
            Ask me anything — code, architecture, document analysis, or even generate an image.
            I'm running locally via Ollama, so your data stays private.
          </p>
          <!-- Suggestion chips -->
          <div class="flex flex-wrap gap-2 justify-center mt-2">
            <button
              *ngFor="let chip of suggestionChips"
              (click)="sendSuggestion(chip)"
              class="px-4 py-2 rounded-full text-sm bg-basalt-surface border border-basalt-border
                     text-basalt-text hover:border-basalt-accent hover:text-basalt-accent transition-colors"
            >
              {{ chip }}
            </button>
          </div>
        </div>

        <!-- Messages -->
        <ng-container *ngFor="let msg of messages; trackBy: trackById">
          <app-message-bubble [message]="msg" />
        </ng-container>

        <!-- Invisible scroll anchor -->
        <div #scrollAnchor></div>
      </main>

      <!-- ── Input Bar ───────────────────────────────────────── -->
      <footer class="flex-shrink-0 border-t border-basalt-border bg-basalt-bg">
        <app-chat-input
          [disabled]="isStreaming"
          (messageSent)="onMessageSent($event)"
        />
        <p class="text-center text-xs text-basalt-muted pb-3">
          Basalt may make mistakes. Always verify critical information.
        </p>
      </footer>

    </div>
  `,
})
export class ChatComponent implements AfterViewChecked {
  @ViewChild('scrollAnchor') scrollAnchor!: ElementRef;
  @ViewChild('scrollContainer') scrollContainer!: ElementRef;

  messages: ChatMessage[] = [];
  isStreaming = false;
  modelLabel = 'llama3.2:1b • local';

  readonly suggestionChips = [
    'Explain the SOLID principles with Java examples',
    'Write a Spring Boot REST controller with WebFlux',
    'Review this code for performance issues',
    'Generate an image: a glowing volcano at night',
  ];

  private shouldScroll = false;

  constructor(private chatService: ChatService) {}

  ngAfterViewChecked(): void {
    if (this.shouldScroll) {
      this.scrollToBottom();
      this.shouldScroll = false;
    }
  }

  onMessageSent(event: SendEvent): void {
    // 1. Add user message
    this.addMessage('user', event.text);

    const imagePrompt = this.getImagePrompt(event.text, event.isImageRequest);

    if (imagePrompt) {
      this.handleImageRequest(imagePrompt);
    } else {
      this.handleChatRequest(event.text, event.useRag);
    }
  }

  sendSuggestion(text: string): void {
    this.addMessage('user', text);
    const imagePrompt = this.getImagePrompt(text);
    if (imagePrompt) {
      this.handleImageRequest(imagePrompt);
    } else {
      this.handleChatRequest(text, false);
    }
  }

  trackById(_: number, msg: ChatMessage): string {
    return msg.id;
  }

  // ── Private helpers ─────────────────────────────────────────────────────

  private handleChatRequest(text: string, useRag: boolean): void {
    const assistantMsg = this.addMessage('assistant', '');
    assistantMsg.isStreaming = true;
    this.isStreaming = true;

    const stream$ = this.chatService.streamChat({
      message: text,
      useRag,
    });

    stream$.subscribe({
      next: (token) => {
        if (token === '[DONE]') return;
        assistantMsg.content += token;
        this.shouldScroll = true;
      },
      error: (err) => {
        assistantMsg.content += `\n\n⚠️ *Stream error: ${err.message}*`;
        assistantMsg.isStreaming = false;
        this.isStreaming = false;
        this.shouldScroll = true;
      },
      complete: () => {
        assistantMsg.isStreaming = false;
        this.isStreaming = false;
        this.shouldScroll = true;
      },
    });
  }

  private handleImageRequest(prompt: string): void {
    const assistantMsg = this.addMessage('assistant', '🎨 Generating image…');
    assistantMsg.isStreaming = true;
    this.isStreaming = true;

    this.chatService.generateImage({ prompt }).subscribe({
      next: (res) => {
        assistantMsg.content = `Here's your generated image for: *${prompt}*`;
        assistantMsg.imageUrl = res.imageUrl;
        assistantMsg.isStreaming = false;
        this.isStreaming = false;
        this.shouldScroll = true;
      },
      error: (err) => {
        const detail = err?.error?.error || err?.message || 'Unknown error';
        assistantMsg.content = `⚠️ *Image generation failed:* ${detail}`;
        assistantMsg.isStreaming = false;
        this.isStreaming = false;
      },
    });
  }

  private getImagePrompt(text: string, explicitImageMode = false): string | null {
    const trimmed = text.trim();
    if (!trimmed) return null;

    if (explicitImageMode) {
      return trimmed;
    }

    const normalized = trimmed.replace(/[.!?]+$/, '');
    const patterns = [
      /^generate\s+an?\s+image\s*:\s*(.+)$/i,
      /^(?:generate|create|make|draw|paint|render|illustrate)\s+(?:an?\s+)?(?:image|picture|photo|illustration|drawing|artwork|render)(?:\s+of|\s+for)?\s+(.+)$/i,
      /^(?:generate|create|make|draw|paint|render|illustrate)\s+(.+?)\s+(?:image|picture|photo|illustration|drawing|artwork|render)$/i,
      /^(?:image|picture|photo|illustration|drawing|artwork|render)\s+of\s+(.+)$/i,
    ];

    for (const pattern of patterns) {
      const match = normalized.match(pattern);
      const prompt = match?.[1]?.trim();
      if (prompt) {
        return prompt;
      }
    }

    return null;
  }

  private addMessage(role: ChatMessage['role'], content: string): ChatMessage {
    const msg: ChatMessage = {
      id: uuidv4(),
      role,
      content,
      timestamp: new Date(),
    };
    this.messages.push(msg);
    this.shouldScroll = true;
    return msg;
  }

  private scrollToBottom(): void {
    try {
      this.scrollAnchor.nativeElement.scrollIntoView({ behavior: 'smooth' });
    } catch {}
  }
}

