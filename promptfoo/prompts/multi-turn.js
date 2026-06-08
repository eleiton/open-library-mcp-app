// Builds an OpenAI chat messages array from { history, current_turn } vars.
// `history` is a list of {role, content} objects representing the synthetic
// prior conversation. `current_turn` is the new user message we're testing.

module.exports = ({ vars }) => {
  const system = {
    role: 'system',
    content:
      'You are a book-discovery assistant with access to the search-books tool. ' +
      'Call it to answer the user. Do not ask clarifying questions first. ' +
      'When the user refines a prior search, carry forward the existing filters and add the new one.'
  };
  const history = vars.history || [];
  const currentTurn = { role: 'user', content: String(vars.current_turn) };
  return JSON.stringify([system, ...history, currentTurn]);
};
