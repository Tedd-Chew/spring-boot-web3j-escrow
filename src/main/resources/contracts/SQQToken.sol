pragma solidity >=0.8.0 <0.9.0;

  contract SQQToken {
      // ---------- 元数据 ----------
      string public name = "SQQToken";
      string public symbol = "SQQ202430551385";
      uint8 public decimals = 18;
      uint256 public totalSupply;

      // ---------- 权限 ----------
      address public owner;

      modifier onlyOwner() {
          require(msg.sender == owner, "SQQToken: caller is not the owner");
          _;
      }

      // ---------- 状态 ----------
      mapping(address => uint256) private _balances;
      mapping(address => mapping(address => uint256)) private _allowances;

      // ---------- 事件 ----------
      event Transfer(address indexed from, address indexed to, uint256 value);
      event Approval(address indexed owner, address indexed spender, uint256 value);

      constructor() {
          owner = msg.sender;
      }

      // ---------- 只读查询 ----------
      function balanceOf(address account) public view returns (uint256) {
          return _balances[account];
      }

      function allowance(address _owner, address spender) public view returns (uint256) {
          return _allowances[_owner][spender];
      }

      // ---------- 转账 ----------
      function transfer(address to, uint256 value) public returns (bool) {
          require(to != address(0), "SQQToken: transfer to zero address");
          require(_balances[msg.sender] >= value, "SQQToken: insufficient balance");

          _balances[msg.sender] -= value;
          _balances[to] += value;
          emit Transfer(msg.sender, to, value);
          return true;
      }

      // ---------- 授权 ----------
      function approve(address spender, uint256 value) public returns (bool) {
          _allowances[msg.sender][spender] = value;
          emit Approval(msg.sender, spender, value);
          return true;
      }

      // ---------- 代扣转账 ----------
      function transferFrom(address from, address to, uint256 value) public returns (bool) {
          require(to != address(0), "SQQToken: transfer to zero address");
          require(_balances[from] >= value, "SQQToken: insufficient balance");
          require(_allowances[from][msg.sender] >= value, "SQQToken: insufficient allowance");

          _allowances[from][msg.sender] -= value;
          _balances[from] -= value;
          _balances[to] += value;
          emit Transfer(from, to, value);
          return true;
      }

      // ---------- 增发：只有 owner 能调 ----------
      function mint(address to, uint256 value) public onlyOwner {
          require(to != address(0), "SQQToken: mint to zero address");

          totalSupply += value;
          _balances[to] += value;
          emit Transfer(address(0), to, value);
      }

      // ---------- 销毁自己的代币 ----------
      function burn(uint256 value) public {
          require(_balances[msg.sender] >= value, "SQQToken: insufficient balance");

          _balances[msg.sender] -= value;
          totalSupply -= value;
          emit Transfer(msg.sender, address(0), value);
      }
  }