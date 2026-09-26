import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { instrumentVisualProgram } from '../src/matter-flow/visualWorkBudget.ts';

function execute(step, state = {}) {
  const program = instrumentVisualProgram({ init: 'return {};', step, draw: 'return;' });
  const context = vm.createContext({ state, dt: 1/60, params: {}, width:960, height:540 },
    { codeGeneration: { strings:false, wasm:false } });
  new vm.Script(`function step(state,dt,params,width,height){${program.step}}step(state,dt,params,width,height);`)
    .runInContext(context, { timeout:100 });
  return context.state;
}
test('work guards preserve helper functions, template labels and nested numeric array access', () => {
  const state=execute(`const plus=(x)=>x+1;
    function total(x){return plus(x);}
    state.points=[[1],[2]];
    state.points[0].push(total(state.points[1][0]));
    state.label=\`Value: \${state.points[0][1]}\`;
    for(let i=1;i<state.points.length;i++){state.points[i][0]+=1;}
    let count=0;do{count++;}while(count<2);state.count=count;`);
  assert.equal(state.label,'Value: 3');
  assert.equal(state.points[1][0],3);
  assert.equal(state.count,2);
});
test('infinite loops and recursive helpers stop within the local work and depth budgets', () => {
  assert.throws(()=>execute('while(true){}'),/work budget/);
  assert.throws(()=>execute('function recur(){return recur();}recur();'),/call depth/);
});
test('parenthesized arrow expression bodies retain object and arithmetic results', () => {
  const state=execute(`const make=(x)=>({value:x});const square=x=>(x*x);
    state.object=make(3);state.square=square(4);`);
  assert.equal(state.object.value,3);
  assert.equal(state.square,16);
});
test('array guards reject string property escape and excessive growth', () => {
  assert.throws(()=>execute('state.value=state.data[state.key];',{data:[],key:'constructor'}),/array index/);
  const data=Array(1999).fill(0);
  assert.throws(()=>execute('state.data.push(1,2);',{data}),/data budget/);
});
test('local strings cannot grow exponentially through addition, compound writes or templates', () => {
  for (const grow of ['text=text+text;', 'text+=text;', 'text=`${text}${text}`;']) {
    assert.throws(()=>execute(`let text='a';for(let i=0;i<30;i++){${grow}}state.label=text;`),
      /string exceeded its data budget/);
  }
});
test('string guards retain numeric addition and evaluate compound lvalues once', () => {
  const state=execute(`state.rows=[{label:'a'}];state.calls=0;let index=0;
    const next=()=>{state.calls++;return 'b';};
    state.rows[index++].label+=next();state.index=index;
    state.number=2;state.number+=3;state.product=(1+2)*3;`);
  assert.equal(state.rows[0].label,'ab');
  assert.equal(state.calls,1);
  assert.equal(state.index,1);
  assert.equal(state.number,5);
  assert.equal(state.product,9);
});
test('template guards preserve cooked text and convert interpolations before later side effects', () => {
  const state=execute(`state.items=[1];
    const change=()=>{state.items.push(2);return 3;};
    state.label=\`line\\n\${state.items}:\${change()}:\${undefined}\`;`);
  assert.equal(state.label,'line\n1:3:undefined');
  assert.deepEqual([...state.items],[1,2]);
});
