class Calculator {
  constructor() {
    this.currentValue = '0';
    this.previousValue = '';
    this.operator = null;
    this.waitingForOperand = false;
    this.expression = '';
    
    this.resultEl = document.getElementById('result');
    this.expressionEl = document.getElementById('expression');
    
    this.init();
  }

  init() {
    document.querySelectorAll('.key').forEach(key => {
      key.addEventListener('click', (e) => this.handleKeyClick(e));
      key.addEventListener('mousedown', () => this.addRipple(key));
    });
    
    document.addEventListener('keydown', (e) => this.handleKeyboard(e));
    
    this.updateDisplay();
  }

  handleKeyClick(e) {
    const key = e.currentTarget;
    key.classList.add('pressed');
    setTimeout(() => key.classList.remove('pressed'), 100);
    
    if (key.dataset.value !== undefined) {
      this.inputDigit(key.dataset.value);
    } else if (key.dataset.action) {
      this.handleAction(key.dataset.action);
    }
  }

  handleKeyboard(e) {
    const keyMap = {
      '0': '0', '1': '1', '2': '2', '3': '3', '4': '4',
      '5': '5', '6': '6', '7': '7', '8': '8', '9': '9',
      '.': '.', '+': 'add', '-': 'subtract', '*': 'multiply',
      '/': 'divide', 'Enter': 'equals', '=': 'equals',
      'Escape': 'clear', 'Backspace': 'backspace',
      '%': 'percent'
    };

    const action = keyMap[e.key];
    if (!action) return;

    e.preventDefault();
    
    const keyEl = document.querySelector(`[data-value="${action}"], [data-action="${action}"]`);
    if (keyEl) {
      keyEl.classList.add('pressed');
      setTimeout(() => keyEl.classList.remove('pressed'), 100);
    }

    if (['0','1','2','3','4','5','6','7','8','9','.'].includes(action)) {
      this.inputDigit(action);
    } else {
      this.handleAction(action);
    }
  }

  addRipple(key) {
    const ripple = document.createElement('span');
    ripple.className = 'ripple';
    const rect = key.getBoundingClientRect();
    const size = Math.max(rect.width, rect.height);
    ripple.style.width = ripple.style.height = `${size}px`;
    ripple.style.left = `${event.clientX - rect.left - size / 2}px`;
    ripple.style.top = `${event.clientY - rect.top - size / 2}px`;
    key.appendChild(ripple);
    setTimeout(() => ripple.remove(), 400);
  }

  inputDigit(digit) {
    if (this.waitingForOperand) {
      this.currentValue = digit === '.' ? '0.' : digit;
      this.waitingForOperand = false;
    } else {
      if (digit === '.' && this.currentValue.includes('.')) return;
      if (this.currentValue === '0' && digit !== '.') {
        this.currentValue = digit;
      } else {
        this.currentValue += digit;
      }
    }
    this.updateDisplay();
  }

  handleAction(action) {
    switch (action) {
      case 'clear':
        this.clear();
        break;
      case 'toggle-sign':
        this.toggleSign();
        break;
      case 'percent':
        this.percent();
        break;
      case 'add':
      case 'subtract':
      case 'multiply':
      case 'divide':
        this.setOperator(action);
        break;
      case 'equals':
        this.calculate();
        break;
      case 'backspace':
        this.backspace();
        break;
    }
  }

  clear() {
    this.currentValue = '0';
    this.previousValue = '';
    this.operator = null;
    this.waitingForOperand = false;
    this.expression = '';
    this.updateDisplay();
  }

  toggleSign() {
    if (this.currentValue !== '0') {
      this.currentValue = this.currentValue.startsWith('-') 
        ? this.currentValue.slice(1) 
        : '-' + this.currentValue;
      this.updateDisplay();
    }
  }

  percent() {
    const value = parseFloat(this.currentValue);
    if (!isNaN(value)) {
      this.currentValue = String(value / 100);
      this.updateDisplay();
    }
  }

  setOperator(nextOperator) {
    const inputValue = parseFloat(this.currentValue);
    
    if (this.operator && this.waitingForOperand) {
      this.operator = nextOperator;
      this.updateExpressionDisplay(nextOperator);
      return;
    }

    if (this.previousValue === '') {
      this.previousValue = String(inputValue);
    } else if (this.operator) {
      const result = this.performCalculation();
      this.currentValue = String(result);
      this.previousValue = String(result);
    }

    this.waitingForOperand = true;
    this.operator = nextOperator;
    this.updateExpressionDisplay(nextOperator);
    this.updateDisplay();
  }

  updateExpressionDisplay(op) {
    const opSymbols = { add: '+', subtract: '−', multiply: '×', divide: '÷' };
    this.expression = `${this.previousValue} ${opSymbols[op]}`;
  }

  calculate() {
    if (!this.operator || this.waitingForOperand) return;
    
    const result = this.performCalculation();
    this.expression = `${this.expression} ${this.currentValue} =`;
    this.currentValue = String(result);
    this.previousValue = '';
    this.operator = null;
    this.waitingForOperand = true;
    this.updateDisplay();
  }

  performCalculation() {
    const prev = parseFloat(this.previousValue);
    const current = parseFloat(this.currentValue);
    
    if (isNaN(prev) || isNaN(current)) return current;

    let result;
    switch (this.operator) {
      case 'add': result = prev + current; break;
      case 'subtract': result = prev - current; break;
      case 'multiply': result = prev * current; break;
      case 'divide': result = current !== 0 ? prev / current : 'Error'; break;
      default: return current;
    }

    if (result === 'Error') {
      this.showError();
      return 0;
    }

    return Math.round(result * 1e10) / 1e10;
  }

  showError() {
    this.resultEl.textContent = 'Error';
    this.resultEl.style.color = '#ff3b30';
    setTimeout(() => {
      this.clear();
      this.resultEl.style.color = '';
    }, 1500);
  }

  backspace() {
    if (this.waitingForOperand || this.currentValue === '0') return;
    
    this.currentValue = this.currentValue.length > 1 
      ? this.currentValue.slice(0, -1) 
      : '0';
    this.updateDisplay();
  }

  updateDisplay() {
    this.resultEl.classList.add('updating');
    requestAnimationFrame(() => {
      this.resultEl.textContent = this.formatNumber(this.currentValue);
      this.expressionEl.textContent = this.expression;
      this.resultEl.classList.remove('updating');
    });

    this.updateOperatorHighlight();
  }

  formatNumber(numStr) {
    if (numStr === 'Error') return 'Error';
    const num = parseFloat(numStr);
    if (isNaN(num)) return numStr;
    
    if (numStr.includes('.')) {
      const [intPart, decPart] = numStr.split('.');
      return intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + '.' + decPart;
    }
    return num.toLocaleString();
  }

  updateOperatorHighlight() {
    document.querySelectorAll('.key.operator').forEach(key => {
      key.classList.remove('active');
      if (this.operator && key.dataset.action === this.operator && this.waitingForOperand) {
        key.classList.add('active');
      }
    });
  }
}

// Initialize when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => new Calculator());
} else {
  new Calculator();
}
