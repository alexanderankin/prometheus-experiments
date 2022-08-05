const MAX_DELAY = 1000;

async function request() {
  await new Promise(r => setTimeout(r, ~~(Math.random() * MAX_DELAY)));
  let letter = ['A', 'B', 'C', 'D'][~~(Math.random() * 4)];
  await fetch('http://localhost:8080/counting/' + letter).then(r => r.text());
}

async function main(total, limit) {
  let promises = [];
  while (total-- > 0) {
    promises.push(request());
    if (promises.length >= limit) {
      let { index } = await promiseAny(promises);
      promises.splice(index, 1);
    }
  }

  await Promise.allSettled(promises)
  console.log('done');
}

async function promiseAny(promises) {
  return Promise.all(promises.slice().map((original, index) => {
    return Promise.resolve({
      // rejection error is data! this short circuits Promise.all
      then: (r, j) => original.then(data => j({ data, index }), r),
    });
  })).then(err => Promise.reject(err), res => Promise.resolve(res));
}

if (require.main === module) {
  main(20000, 20).then(console.log);
}
